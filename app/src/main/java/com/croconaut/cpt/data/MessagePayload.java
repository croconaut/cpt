package com.croconaut.cpt.data;

import android.os.Parcelable;

import java.util.List;

public interface MessagePayload extends Parcelable {
    List<? extends MessageAttachment> getAttachments();
}
