package com.croconaut.cpt.network;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.util.Log;

import com.croconaut.cpt.R;
import com.croconaut.cpt.common.NotificationId;
import com.croconaut.cpt.common.util.FileUtil;
import com.croconaut.cpt.data.AttachmentIdentifier;
import com.croconaut.cpt.data.Communication;
import com.croconaut.cpt.data.CptClientCommunication;
import com.croconaut.cpt.data.DatabaseManager;
import com.croconaut.cpt.data.DownloadedAttachment;
import com.croconaut.cpt.data.DownloadedAttachmentPreview;
import com.croconaut.cpt.data.MessageAttachmentIdentifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class AttachmentsHelper extends AbstractHelper {
    private static final AtomicInteger receivingIdCounter = new AtomicInteger();
    private static final AtomicInteger   sendingIdCounter = new AtomicInteger();

    private final NotificationManager nm;
    private final DownloadManager dm;

    private class UriFileOutputStream {
        private Uri uri;  // for later usage
        private String path;  // for later converting into uri
        private ParcelFileDescriptor parcelFileDescriptor;    // protected from the GC
        private FileOutputStream fileOutputStream;

        public UriFileOutputStream(Context context, Uri uri) throws FileNotFoundException {
            this.uri = uri;

            this.parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "w");
            if (parcelFileDescriptor != null) {
                fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
            } else {
                throw new FileNotFoundException("Uri not found: " + uri);
            }
        }

        public UriFileOutputStream(String path) throws FileNotFoundException {
            this.path = path;

            fileOutputStream = new FileOutputStream(path);
        }

        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public Uri getUri() {
            return uri;
        }

        public String getPath() {
            return path;
        }

        public boolean isValid() {
            return fileOutputStream != null;
        }

        public void write(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            if (isValid()) {
                fileOutputStream.write(buffer, byteOffset, byteCount);
            }
        }

        public void delete() {
            if (isValid()) {
                if (uri != null) {
                    context.getContentResolver().delete(uri, null, null);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    new File(path).delete();
                }
            }
        }

        public void close() throws IOException {
            if (isValid()) {
                fileOutputStream.close();
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            }
        }
    }

    public AttachmentsHelper(String TAG, Context context, DataInputStream dis, DataOutputStream dos) {
        super(TAG, context, dis, dos);

        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public Map<String, UriIdentifier> getUriIdentifiers(Map<String, List<MessageAttachmentIdentifier>> requests) {
        Map<String, UriIdentifier> uriIdentifiers = new HashMap<>();    // make it a map for easier lookup

        for (String sourceUri : requests.keySet()) {
            UriIdentifier uriIdentifier = uriIdentifiers.get(sourceUri);
            if (uriIdentifier == null) {
                uriIdentifier = new UriIdentifier(sourceUri);
                uriIdentifiers.put(sourceUri, uriIdentifier);
            }
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : requests.get(sourceUri)) {
                // duplicate entries are ignored
                if (messageAttachmentIdentifier.getStorageDirectory() == null) {
                    uriIdentifier.addPrivateStorage();
                } else {
                    uriIdentifier.addPublicStorage(messageAttachmentIdentifier.getStorageDirectory());
                }
            }
        }

        return uriIdentifiers;
    }

    public Set<MessageAttachmentIdentifier> getMessageAttachmentIdentifiers(Map<String, List<MessageAttachmentIdentifier>> requests) {
        Set<MessageAttachmentIdentifier> messageAttachmentIdentifiers = new HashSet<>();

        for (List<MessageAttachmentIdentifier> uriMessageAttachmentIdentifiers: requests.values()) {
            messageAttachmentIdentifiers.addAll(uriMessageAttachmentIdentifiers);
        }

        return messageAttachmentIdentifiers;
    }

    public void readUri(UriIdentifierResponse uriIdentifierResponse, List<MessageAttachmentIdentifier> messageAttachmentIdentifiers, boolean isP2pConnection) throws IOException, InterruptedException {
        final String       name = uriIdentifierResponse.getName();
        final long       length = uriIdentifierResponse.getLength();
        final long lastModified = uriIdentifierResponse.getLastModified();
        final String       type = uriIdentifierResponse.getType();

        // the only reason why we can't use just sourceUri as a key is the fact we can have multiple
        // target storage directories for the same source uri and of course we want to have
        // them unique (i.e. only one file in IMAGES, one file in DOWNLOADS etc)
        final Map<AttachmentIdentifier, UriFileOutputStream> uriFileOutputStreams = new HashMap<>();

        // TODO: CountDownLatch can be initialized only once, maybe we can implement some kind of dynamic queue later
        final Map<String, AttachmentIdentifier> publicFilePaths = new HashMap<>();

        for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
            CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                    Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOADING,
                    messageAttachmentIdentifier,
                    new DownloadedAttachmentPreview(
                            messageAttachmentIdentifier.getSourceUri(),
                            name,
                            length,
                            lastModified,
                            type,
                            messageAttachmentIdentifier.getStorageDirectory()
                    ),
                    -1
            );

            if (!uriFileOutputStreams.containsKey(messageAttachmentIdentifier.getAttachmentIdentifier())) {
                if (messageAttachmentIdentifier.getStorageDirectory() == null) {
                    // private storage => use sourceUri and let the client app handle it
                    Uri uri = Uri.parse(messageAttachmentIdentifier.getSourceUri());
                    uri = context.getContentResolver().insert(uri, new ContentValues());
                    uriFileOutputStreams.put(messageAttachmentIdentifier.getAttachmentIdentifier(),
                            new UriFileOutputStream(context, uri)
                    );
                } else {
                    // public storage => use the type and create uri here

                    // we don't use sourceUri because the authority can differ or be not present on the target device (e.g. Storage Access Framework)
                    // on the other hand, if we're on API <= 18 and the directory says Environment.DIRECTORY_DOCUMENTS, we create it, no harm done
                    File publicDirectoryFile = Environment.getExternalStoragePublicDirectory(messageAttachmentIdentifier.getStorageDirectory());
                    //noinspection ResultOfMethodCallIgnored
                    publicDirectoryFile.mkdirs();

                    File file = new File(publicDirectoryFile, name);
                    if (!file.createNewFile()) {
                        // file not created because it exists
                        file = File.createTempFile(
                                FileUtil.getBaseName(name).concat("_"),
                                FileUtil.getExtension(name), publicDirectoryFile
                        );
                    }
                    publicFilePaths.put(file.getAbsolutePath(), messageAttachmentIdentifier.getAttachmentIdentifier());

                    // we have to create an instance to keep track of unique keys
                    uriFileOutputStreams.put(messageAttachmentIdentifier.getAttachmentIdentifier(),
                            new UriFileOutputStream(file.getAbsolutePath())
                    );
                }
            }
        }

        int bytesPerSecond = 0;
        int notificationId = getAndIncrement(NotificationId.DOWNLOAD_BASE, receivingIdCounter);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(name)
                .setSmallIcon(R.drawable.ic_wifon)
                .setOngoing(true)
                .setProgress((int) length, 0, false)
                .setContentIntent(CptClientCommunication.getMessageAttachmentDownloadNotificationPendingIntent(
                        // take the first request -- cancel can occur for only one attachment at time
                        context, helper, 0, messageAttachmentIdentifiers.get(0)
                        )
                );
        nm.notify(notificationId, builder.build());

        try {
            byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
            int len;
            long total = 0;
            long startTime = System.currentTimeMillis();
            long previousTotal = 0;
            while (total != length && (len = dis.read(buffer, 0, (int) Math.min(buffer.length, length - total))) != -1) {
                total += len;
                long currentTime = System.currentTimeMillis();
                if (currentTime - startTime < 1000) {
                    currentTime = startTime + 1000;
                }
                bytesPerSecond = (int) ((total * 1000) / (currentTime - startTime));

                if (total - previousTotal >= (128*1024) || total == length) {
                    previousTotal = total;
                    builder.setProgress((int) length, (int) total, false);
                    builder.setContentText(context.getResources().getString(R.string.cpt_notif_p2p_download,
                            Formatter.formatShortFileSize(context, bytesPerSecond)));
                    nm.notify(notificationId, builder.build());
                }

                for (UriFileOutputStream uriStream : uriFileOutputStreams.values()) {
                    uriStream.write(buffer, 0, len);

                    if (Thread.interrupted()) {
                        throw new InterruptedIOException();
                    }
                }
            }

            if (total != uriIdentifierResponse.getLength()) {
                throw new IOException("read() ended prematurely");
            }
        } catch (IOException e) {   // InterruptedIOException, too
            // if something went wrong, delete all uris
            for (UriFileOutputStream uriStream : uriFileOutputStreams.values()) {
                uriStream.delete();
            }
            for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED,
                        messageAttachmentIdentifier
                );
            }
            throw e;
        } finally {
            nm.cancel(notificationId);

            for (UriFileOutputStream uriStream : uriFileOutputStreams.values()) {
                uriStream.close();
            }
        }

        Log.d(TAG, "Received at " + Formatter.formatFileSize(context, bytesPerSecond) + "/s: " + uriIdentifierResponse);

        // ServiceConnection: Like many callbacks from the system, the methods on this class are called from the main thread of your process.
        final CountDownLatch countDownLatch = new CountDownLatch(publicFilePaths.size());
        MediaScannerConnection.scanFile(
                context,
                publicFilePaths.keySet().toArray(new String[publicFilePaths.keySet().size()]),
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        AttachmentIdentifier attachmentIdentifier = publicFilePaths.get(path);
                        if (attachmentIdentifier != null && uriFileOutputStreams.containsKey(attachmentIdentifier)) {
                            uriFileOutputStreams.get(attachmentIdentifier).setUri(uri);
                            countDownLatch.countDown();
                        } else {
                            Log.e(TAG, "Path " + path + " not found in publicFilePaths and/or uriFileOutputStreams");
                        }
                    }
                }
        );
        countDownLatch.await(10, TimeUnit.SECONDS); // should be more than enough...

        // if we made it here, every copy is stored properly
        Date now = new Date();
        for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
            DatabaseManager.markDownloadUriAsReceived(context,
                    messageAttachmentIdentifier
            );

            UriFileOutputStream uriFileOutputStream = uriFileOutputStreams.get(messageAttachmentIdentifier.getAttachmentIdentifier());
            if (uriFileOutputStream.getUri() != null) {
                CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOADED,
                        messageAttachmentIdentifier,
                        new DownloadedAttachment(
                                uriFileOutputStream.getUri(),
                                messageAttachmentIdentifier.getStorageDirectory(),
                                name,
                                messageAttachmentIdentifier.getSourceUri()
                        ),
                        now,
                        bytesPerSecond
                );
            } else {
                // due to the failed CountDownLatch
                CptClientCommunication.messageAttachmentDownloadAction(context, helper,
                        Communication.ACTION_MESSAGE_ATTACHMENT_DOWNLOAD_CONFIRMED,
                        messageAttachmentIdentifier
                );
            }

            Log.v(TAG, "Announced: " + messageAttachmentIdentifier);
        }

        // crap, now even this... (uriFileOutputStreams are all closed by now)
        for (UriFileOutputStream uriStream : uriFileOutputStreams.values()) {
            if (uriStream.getPath() != null) {
                dm.addCompletedDownload(
                        name, // title
                        new File(uriStream.getPath()).getName(), // description
                        true, // isMediaScannerScannable
                        type != null ? type : "application/octet-stream",
                        uriStream.getPath(),
                        length,
                        false // showNotification
                );
            }
        }
    }

    public void writeUri(UriIdentifierResponse uriIdentifierResponse, List<MessageAttachmentIdentifier> messageAttachmentIdentifiers, boolean isP2pConnection) throws IOException {
        final String       name = uriIdentifierResponse.getName();
        final long       length = uriIdentifierResponse.getLength();
        final long lastModified = uriIdentifierResponse.getLastModified();
        final String       type = uriIdentifierResponse.getType();

        Uri uri = Uri.parse(uriIdentifierResponse.getSourceUri());
        ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
        if (parcelFileDescriptor != null) {
            FileInputStream fis = new FileInputStream(parcelFileDescriptor.getFileDescriptor());

            for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                CptClientCommunication.messageAttachmentUploadAction(context, helper,
                        isP2pConnection ? Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_RECIPIENT : Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADING_TO_APP_SERVER,
                        messageAttachmentIdentifier
                );
            }

            int bytesPerSecond = 0;
            int notificationId = getAndIncrement(NotificationId.UPLOAD_BASE, sendingIdCounter);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setContentTitle(name)
                    .setSmallIcon(R.drawable.ic_wifon)
                    .setOngoing(true)
                    .setProgress((int) length, 0, false)
                    .setContentIntent(CptClientCommunication.getMessageAttachmentUploadNotificationPendingIntent(
                            // take the first request -- cancel can occur for only one attachment at time
                            context, helper, 0, messageAttachmentIdentifiers.get(0)
                    ));
            nm.notify(notificationId, builder.build());

            try {
                byte[] buffer = new byte[NetworkUtil.ATTACHMENT_BUFFER_SIZE];
                int len;
                long total = 0;
                long startTime = System.currentTimeMillis();
                long previousTotal = 0;
                while (total != length && (len = fis.read(buffer, 0, (int) Math.min(buffer.length, length - total))) != -1) {
                    dos.write(buffer, 0, len);
                    total += len;
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - startTime < 1000) {
                        currentTime = startTime + 1000;
                    }
                    bytesPerSecond = (int) ((total * 1000) / (currentTime - startTime));

                    if (Thread.interrupted()) {
                        throw new InterruptedIOException();
                    }

                    if (total - previousTotal > (128*1024)) {
                        previousTotal = total;
                        builder.setProgress((int) length, (int) total, false);
                        builder.setContentText(context.getResources().getString(R.string.cpt_notif_p2p_upload,
                                Formatter.formatShortFileSize(context, bytesPerSecond)));
                        nm.notify(notificationId, builder.build());
                    }
                }

                if (total != uriIdentifierResponse.getLength()) {
                    throw new IOException("read() ended prematurely");
                }
            } catch (IOException e) {   // InterruptedIOException, too
                for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                    CptClientCommunication.messageAttachmentUploadAction(context, helper,
                            Communication.ACTION_MESSAGE_ATTACHMENT_UPLOAD_CONFIRMED,
                            messageAttachmentIdentifier
                    );
                }
                throw e;
            } finally {
                nm.cancel(notificationId);

                fis.close();
                parcelFileDescriptor.close();
            }

            Date now = new Date();

            uriIdentifierResponse.setBytesPerSecondSent(bytesPerSecond);
            uriIdentifierResponse.setTimeSent(now);
            Log.d(TAG, "Sent [" + uri + "] at " + Formatter.formatFileSize(context, bytesPerSecond) + "/s: " + uriIdentifierResponse);

            if (isP2pConnection) {
                for (MessageAttachmentIdentifier messageAttachmentIdentifier : messageAttachmentIdentifiers) {
                    DatabaseManager.markUploadUriAsSentToRecipient(context,
                            messageAttachmentIdentifier,
                            true
                    );
                    CptClientCommunication.messageAttachmentUploadAction(context, helper,
                            Communication.ACTION_MESSAGE_ATTACHMENT_UPLOADED_TO_RECIPIENT,
                            messageAttachmentIdentifier,
                            now,
                            bytesPerSecond
                    );
                }
            }
        } else {
            throw new FileNotFoundException("Uri not found: " + uri);
        }
    }
}
