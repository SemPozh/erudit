package com.erudit.user.application.port;

public interface Passwords {
    String encode(CharSequence password);
    boolean matches(CharSequence password, String encoded);
}
