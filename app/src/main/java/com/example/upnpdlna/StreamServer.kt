package com.example.upnpdlna

import fi.iki.elonen.NanoHTTPD


class StreamServer(port: Int) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession?): Response {
        // Hier kannst du die Logik implementieren, um die Video-Datei zu servieren
        return newFixedLengthResponse("Hello, this is the stream server.")
    }
}