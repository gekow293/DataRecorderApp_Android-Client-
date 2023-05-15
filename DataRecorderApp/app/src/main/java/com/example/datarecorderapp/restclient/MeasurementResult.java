package com.example.datarecorderapp.restclient;

import lombok.Data;

@Data
public class MeasurementResult {
    private String latitude;
    private String longitude;
    private String altitude;
    //private String time;
    private String nowTimeSignal;
//    private String theta;
//    private String delta;
    private String id;
}
