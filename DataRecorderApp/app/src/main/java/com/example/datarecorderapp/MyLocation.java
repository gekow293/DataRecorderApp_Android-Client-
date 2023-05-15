package com.example.datarecorderapp;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import androidx.annotation.NonNull;

import lombok.SneakyThrows;

public class MyLocation implements LocationListener {
    private MyLocationInterface myLocationInterface;
    //слушатель обновления location, при изменении location(передались данные с провайдера) вызывается функция интерфейса, который передает данные в MainActivity
    @SneakyThrows
    @Override
    public void onLocationChanged(@NonNull Location location) {
        myLocationInterface.getNowLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {

    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {

    }
    //cеттер для связки MainActivity и интерефейса MuLocationInterface
    public void setMyLocationInterface(MyLocationInterface myLocationInterface) {
        this.myLocationInterface = myLocationInterface;
    }
}
