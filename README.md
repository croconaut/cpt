This is the Wi-Fi / Wi-Fi Direct based peer to peer communication framework for Android. It stands for "Croconaut Public Transport", Croconaut was a "startup" from the times when we thought we can finish the product in three months. :) We'll use the wiki and the issues tracker for now.

Checkout the [wiki](https://github.com/croconaut/cpt/wiki) for detailed description and information or follow the quick step-by-step guide and surely take a look at [WiFON mini](https://github.com/croconaut/wifon-mini) with a small demonstration how to put all together:

# Gradle

    repositories {
        maven {
            url "https://mymavenrepo.com/repo/fsD4SRhSQewcguNLyevk"
        }
        maven {
            url "https://s3.amazonaws.com/repo.commonsware.com"
        }
    }

    dependencies {
        compile 'com.croconaut:cpt:1.0.2'
    }

# Add the broadcast receiver
AndroidManifest.xml:

    <receiver android:name=".MyBroadcastReceiver" />

MyBroadcastReceiver.java:

    import com.croconaut.cpt.ui.CptReceiver;
    
    public class MyBroadcastReceiver extends CptReceiver {
        // implement the interface, most important functions are:
        @Override
        protected void onNearbyPeers(Context context, ArrayList<NearbyUser> nearbyUsers) {
            // NearbyUser.crocoId shall be used as the recipient's ID
        }
        @Override
        protected void onNewMessage(Context context, long messageId, Date receivedTime, IncomingMessage incomingMessage) {
            // read / request attachments and get your app data (like a text message)
        }
    }

# Register the broadcast receiver and username
    Communication.register(this, "Miro Kropacek", MyBroadcastReceiver.class);
    
# Enable *CPT*
Technically speaking, you don't have to enable *CPT* if you don't want to transmit it immediately, the message will be stored anyway.

    CptController cptController = new CptController(this);
    cptController.setMode(LinkLayerMode.FOREGROUND);

# Send a message
    OutgoingPayload outgoingPayload = new OutgoingPayload("Hi there from the other device");    // can be anything Serializable, not only a string
    OutgoingMessage outgoingMessage = new OutgoingMessage(targetCrocoId);
    outgoingMessage.setPayload(outgoingPayload);
    Communication.newMessage(getContext(), outgoingMessage);
