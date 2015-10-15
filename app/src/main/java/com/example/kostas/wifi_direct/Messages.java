package com.example.kostas.wifi_direct;

import java.io.Serializable;

import java.net.URL;



public class Messages implements Serializable{

/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
    public String flag;
    public String name;
    public int thr;
    public int size;
    public URL url;
    public int from,to;
    byte [] buffer;

	public Messages(){
        this.flag="ack";
	}

    public Messages(String str){
        this.flag="terminate";
    }

    public Messages(int thr,String name){
        this.flag="thrput";
        this.thr=thr;
        this.name=name;
    }

    public Messages(String name,int size){
        this.flag="name";
        this.size=size;
        this.name=name;
    }
    public Messages(String name,URL url,int size){
        this.flag="file_req";
        this.url=url;
        this.size=size;
        this.name=name;
    }
    public Messages(String name,byte [] buffer){
        this.flag="compl_file";
        this.buffer=buffer;
        this.name=name;
    }
    public Messages(boolean flag){

        this.flag="not_found";
    }
    public Messages(String name,byte[] buffer,int from,int to){
        this.flag="file_part";
        this.from=from;
        this.to=to;
        this.name=name;
        this.buffer=buffer;
    }

    public Messages(URL url,String name,int from,int to){
        this.flag="range_req";
        this.url=url;
        this.name=name;
        this.from=from;
        this.to=to;
    }

	
	@Override
	public String toString(){
		return flag;
	
	}
}
