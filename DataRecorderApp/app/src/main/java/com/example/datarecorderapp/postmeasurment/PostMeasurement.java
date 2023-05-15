package com.example.datarecorderapp.postmeasurment;

import com.example.datarecorderapp.restclient.MeasurementResult;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface PostMeasurement {
    @Multipart
    @POST("/postmeasurement")
    Call<Placement> postMeasurement(@Part MultipartBody.Part file,
                         @Part("excel") MeasurementResult myMeas);
}

