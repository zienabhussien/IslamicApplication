package com.example.myislamicapplication.data.prayersnotification;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myislamicapplication.data.networking.PrayersRetrofit;
import com.example.myislamicapplication.data.pojo.prayertimes.Datum;
import com.example.myislamicapplication.data.pojo.prayertimes.PrayerAPIResponse;
import com.example.myislamicapplication.data.pojo.prayertimes.PrayerTiming;
import com.example.myislamicapplication.data.pojo.prayertimes.Timings;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public class RegisterPrayerTimesWorker extends Worker {
    public RegisterPrayerTimesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Calendar calendar = Calendar.getInstance();
            PrayersPreferences preferences = new PrayersPreferences(getApplicationContext());
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.YEAR);
           // int day = calendar.get(Calendar.DAY_OF_MONTH);
            String city = preferences.getCity();
            String country = preferences.getCountry();
            int method = preferences.getMethod();
            Response<PrayerAPIResponse> timeResponse = PrayersRetrofit.getAPI().getPrayers(city, country, method, month, year).execute();
            if(timeResponse.isSuccessful()){
                List<Datum> data = timeResponse.body().getData();
                for (int i = 0; i < data.size(); i++) {
                    int day = i+1;
                    Datum datum = data.get(i);
                    Timings timings = datum.getTimings();
                    ArrayList<PrayerTiming> prayers = convertFromTimings(timings);

                    prayers.forEach(prayerTiming -> {
                        String prayerTag = ""+year+"/"+month+"/"+day+" "+prayerTiming.getPrayerName();
                        Data input = new Data.Builder()
                                .putString(AzanNotificationConstants.TITLE_KEY, prayerTiming.getPrayerName())
                                .putString(AzanNotificationConstants.CONTENT_KEY,"حي على الصلاة")
                                .build();
                        OneTimeWorkRequest registerPrayerRequest =
                                new OneTimeWorkRequest.Builder(AzanNotificationWorker.class)
                                        .addTag(prayerTag)
                                        .setInitialDelay(calculatePrayerDelay(year,month,day,prayerTiming), TimeUnit.MILLISECONDS)
                                        .setInputData(input)
                                        .build();
                        WorkManager.getInstance(getApplicationContext())
                                .enqueueUniqueWork(prayerTag, ExistingWorkPolicy.REPLACE,registerPrayerRequest);

                    });
                }

            }else {
                return Result.failure();
            }

        } catch (IOException e) {
            e.printStackTrace();
            return Result.retry();
        }
        return null;
    }

    private long calculatePrayerDelay(int year,int month,int day,PrayerTiming prayerTiming) {
        String pattern =  "yyyy/MM/dd HH:mm";
        DecimalFormat decimalFormat = new DecimalFormat("00");
        String time = prayerTiming.getPrayerTime().split(" ")[0];
        String prayerDate = ""+year+"/"+decimalFormat.format(month)+"/"+decimalFormat.format(day)+" "+time;
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        try {
            Date date = format.parse(prayerDate);
            long currentTime = System.currentTimeMillis();
            return Math.abs(date.getTime()-currentTime);

        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }


    }

    ArrayList<PrayerTiming> convertFromTimings(Timings timings){
        ArrayList<PrayerTiming> res = new ArrayList<>();
        res.add(new PrayerTiming("Fajr",timings.getFajr()));
        res.add(new PrayerTiming("Dhur",timings.getDhuhr()));
        res.add(new PrayerTiming("Asr",timings.getAsr()));
        res.add(new PrayerTiming("Maghrib",timings.getMaghrib()));
        res.add(new PrayerTiming("Isha",timings.getIsha()));
        return res;
    }
}
