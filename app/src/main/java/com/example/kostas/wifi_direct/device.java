package com.example.kostas.wifi_direct;

import java.net.Socket;

/**
 * Created by Kostas on 12-01-2015.
 */
public class device {
    Socket socket;
    int thr;
    public device(Socket sckt,int thr){
        this.socket=sckt;
        this.thr=thr;
    }

}
