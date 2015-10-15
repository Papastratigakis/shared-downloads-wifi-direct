package com.example.kostas.wifi_direct;

/**
 * Created by Kostas on 13-01-2015.
 */
public class filePart implements Comparable<filePart>{
    public int from;
    public int to;
    public byte [] buffer;

    public filePart( byte[] buffer,int from, int to) {
        this.from = from;
        this.to = to;
        this.buffer = buffer;
    }
    public int compareTo(filePart comparePart) {

        int comparePosition = comparePart.from;

        return this.from - comparePart.from;


    }


}
