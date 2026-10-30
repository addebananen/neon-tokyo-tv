package com.lumera.app.siri;

/** Runs in Shizuku's shell-uid process; Neon calls it to (re)start the Siri Remote bridge. */
interface ISiriRunner {
    /** Runs one shell command, waits for it to return, and gives back its combined output. */
    String run(String command) = 1;
    void destroy() = 16777114; // Shizuku's reserved destroy transaction
}
