package com.tikitaka.document.dto;

public enum DocumentNoteType {
    NONE, SHARED, PRIVATE, ALL;

    public boolean includesShared() { return this == SHARED || this == ALL; }
    public boolean includesPrivate() { return this == PRIVATE || this == ALL; }
}
