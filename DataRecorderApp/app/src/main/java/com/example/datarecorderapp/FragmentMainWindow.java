package com.example.datarecorderapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.datarecorderapp.postmeasurment.Placement;
import com.example.datarecorderapp.postmeasurment.PostMeasurement;
import com.example.datarecorderapp.restclient.MeasurementResult;
import com.example.datarecorderapp.sntpclient.NTPUDPClient;
import com.example.datarecorderapp.sntpclient.SNTPClient;

import org.apache.commons.net.ntp.TimeInfo;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RuntimePermissions
public class FragmentMainWindow extends Fragment implements View.OnClickListener, MyLocationInterface {
    private static final int REQUEST_SET_TIME = 1001;
    private static final int DEVICE_ADMIN_ADD_RESULT_ENABLE = 1;
    private NavController navController; //класс для взаиможействия между фрагментами (активити)
    /* Переменные для записи звука */
    int myBufferSize = 64; //размер буффера для хранения данных с микрофона
    AudioRecord audioRecord; //рекордер для запись звука с микрофона, но с возможностью редактирования данных до записи в файл (исп-ся для определения превышения порага звука)
    private boolean isReading = false;
    //private byte max_noise = (byte) 20000; //порог звука срабатывания записи данных
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private final String recordPermission = Manifest.permission.RECORD_AUDIO;
    private final int REQUEST_PERMISSION_CODE_AUDIO_SD = 1000;
    private final int REQUEST_PERMISSION_CODE_GPS = 100;
    private String recordFile;
    private String recordFileName;
    private boolean flag_res_audio_sd = false;
    private boolean flag_res_gps = false;
    /* управление на главном окне */
    private Button list_button;
    private Button record_button;
    private ToggleButton toggle_button;
    private ToggleButton toggle_start;
    private TextView filenameText;
    private TextView inputNoise;
    private TextView nameOfPhoneView;
    private TextView host_server;
    private TextView host_retrofit;
    private TextView portServer;
    private TextView portDb;
    private Chronometer timer;
    private ProgressBar progressBar;
    /* для data.xls */
    HSSFWorkbook wb;
    HSSFSheet sheet;
    private int rowNum;
    /* Местоположение */
    private TextView latitude;
    private TextView longitude;
    private TextView nowtime;
    private TextView realTimeOfSever;
    private TextView offset;
    private TextView delay;
    private TextView altitude;
    private LocationManager locationManager;
    private MyLocation myLocation; //класс для обновления местоположения
    private File file;
    //SNTPClient data
    private SNTPClient sntpClient;
    private String namePhone;
    private String ipServer;
    private String ipRetrofit;
    private String portOfServer;
    private String portOfDb;
    private String noise;
    private long diffTime;

//    private ComponentName deviceAdmin;
//    private DevicePolicyManager dpm;
    // обработчик потока - обновляет сведения о времени
    // Создаётся в основном UI-потоке
    private Handler handler;
    private OnlineExchangeRun exchangeRun;
    //Формат
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    private long timeReceiveDateServer;
    private long theta;
    private long delta;
    private long timeWhenSignalCome;
    private String requestOutServer;
    private boolean flag_server = false;
    private boolean flag_client = false;
    private NTPUDPClient ntpudpClient;
    private boolean timeOfServerUpdateBoolean;
    private long regularTimeOfServer;
    private long currentSysTime;
    private long nowTimeSignal;

    public FragmentMainWindow() {
        // Конструктор
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main_window, container, false);
    }

    private void createAudioRecorder() {
        int sampleRate = 8000; //частота дискретизации
        int channelConfig = AudioFormat.CHANNEL_IN_MONO; //один записывающий канал
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //битность каждого отсчета
        int minInternalBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int internalBufferSize = minInternalBufferSize * 4;
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, internalBufferSize); // настройка адуиорекордера по нашим параметрам
    }

    @SuppressLint("SetTextI18n")
    @SneakyThrows
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ipServer = "192.168.1.89";
        ipRetrofit = "192.168.1.89";
        ntpudpClient = new NTPUDPClient();
        sntpClient = new SNTPClient();
        exchangeRun = new OnlineExchangeRun();
        host_retrofit = (TextView) view.findViewById(R.id.adrr_retrofit);
        host_retrofit.setText(ipRetrofit);
        host_server = (TextView) view.findViewById(R.id.adrr_server);
        host_server.setText(ipServer);
        portServer = view.findViewById(R.id.port_server);
        portServer.setText("4409");
        portDb = view.findViewById(R.id.port_db);
        portDb.setText("8080");
        inputNoise = view.findViewById(R.id.noise);
        inputNoise.setText("10000");
        nameOfPhoneView = view.findViewById(R.id.name_phone);
        nameOfPhoneView.setText("1_smartphone");
        navController = Navigation.findNavController(view);
        list_button = (Button) view.findViewById(R.id.record_list_btn);
        record_button = (Button) view.findViewById(R.id.record_btn);
        toggle_button = view.findViewById(R.id.toggleButton);
        toggle_start = (ToggleButton) view.findViewById(R.id.toggleStart);
        timer = (Chronometer) view.findViewById(R.id.record_timer);
        filenameText = (TextView) view.findViewById(R.id.record_filename);
        list_button.setOnClickListener(this);
        record_button.setOnClickListener(this);
        toggle_button.setOnClickListener(this);
        toggle_start.setOnClickListener(this);
//        host_retrofit.setOnClickListener(this);
//        host_server.setOnClickListener(this);
//        portDb.setOnClickListener(this);
//        portServer.setOnClickListener(this);
//        inputNoise.setOnClickListener(this);
//        nameOfPhoneView.setOnClickListener(this);
        progressBar = (ProgressBar)view.findViewById(R.id.progressBar);
        //progressBar.setOnClickListener(this);
        latitude = (TextView) view.findViewById(R.id.latitude);
        longitude = (TextView) view.findViewById(R.id.longitude);
        nowtime = (TextView) view.findViewById(R.id.time);
        realTimeOfSever = (TextView) view.findViewById(R.id.time_of_server);
        altitude = (TextView) view.findViewById(R.id.altitude);
        offset = (TextView) view.findViewById(R.id._offset);
        delay = (TextView)view.findViewById(R.id._dalay);
        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        myLocation = new MyLocation();
        myLocation.setMyLocationInterface(this); //связываем интрефейс, который передает location с MainActivity
//        deviceAdmin = new ComponentName(getActivity(), SampleAdminReceiver.class);
//        dpm = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);

        StrictMode.ThreadPolicy policy = new
                StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        createAudioRecorder();
        if(!checkPermissionsAudio_SD())
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE_AUDIO_SD);
        else
        {
            if(!flag_res_audio_sd) {
                Toast.makeText(getActivity(), "Permission Audio and SD Granted", Toast.LENGTH_SHORT).show();
                flag_res_audio_sd = true;
            }
        }
        checkGPSPermissions();

        new Handler().postDelayed(new Runnable() {
            @SneakyThrows
            public void run() {
                AlarmManager alarmManager= (AlarmManager)getContext().getSystemService(Activity.ALARM_SERVICE);
                alarmManager.setTime(getTimeFromNtpServer(String.valueOf(host_server)));
            }
        }, 60000);


        //checkOnChangeTimePermission();
        //DeviceOwnerHelper.getInstance().getDevicePolicyManager().setPermissionGrantState(new ComponentName(getContext(), DeviceOwnerReceiver.class), getContext().getPackageName(), Manifest.permission.SET_TIME, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);

    }

//    @Override
//    public void onResume() {
//        super.onResume();
//        refreshButtons();
//    }

//    @NeedsPermission({Manifest.permission.SET_TIME})
//    void checkOnChangeTimePermission () {
//        int permissionCheck = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.SET_TIME);
//        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.SET_TIME}, REQUEST_SET_TIME);
//        } else {
//            if(!flag_res_set_time) {
//                Toast.makeText(getActivity(), "Permission set time Granted", Toast.LENGTH_SHORT).show();
//                flag_res_set_time = true;
//            }
//        }
//    }

    public void postByRetrofit() throws IOException {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + host_retrofit.getText().toString() + ":" + 8080) // сервер базы данных
                .addConverterFactory(GsonConverterFactory.create()) // говорим ретрофиту что для сериализации необходимо использовать GSON
                .build();
        PostMeasurement service = retrofit.create(PostMeasurement.class);
        MeasurementResult measurementResult = new MeasurementResult();

        measurementResult.setAltitude(altitude.getText().toString());
        measurementResult.setLatitude(latitude.getText().toString());
        measurementResult.setLongitude(longitude.getText().toString());
        measurementResult.setNowTimeSignal(String.valueOf(nowTimeSignal));
//        measurementResult.setTheta(String.valueOf(theta));
//        measurementResult.setDelta(String.valueOf(delta));
        measurementResult.setId(nameOfPhoneView.getText().toString());

        MultipartBody.Part soundFile = null;
        String recordPath = getActivity().getExternalFilesDir("/").getAbsolutePath();
        if (recordPath != null) {
            File file = new File(recordPath + "/" + recordFile);
            File copyFile = new File(recordPath + "/copyfiles/" + recordFile);
            copyFile(file, copyFile);
            Log.i("Register", "post file from android: " + file.getName());
            RequestBody requestFile =
                    RequestBody.create(MediaType.parse("video/*"), copyFile);
            soundFile = MultipartBody.Part.createFormData("audio.mp4", copyFile.getName(), requestFile);
        }
        Call<Placement> call = service.postMeasurement(soundFile,
                measurementResult);
        call.enqueue(new Callback<Placement>() {
            @Override
            public void onResponse(Call<Placement> call, Response<Placement> response) {
                if (response.isSuccessful()) {
                    Log.e("RetrofitTag", response.body().getContent());
                } else {
                    Log.e("RetrofitTag", response.message());
                }
            }
            @Override
            public void onFailure(Call<Placement> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

//    @SuppressLint("StaticFieldLeak")
//    public class AsyncRequest extends AsyncTask<Void, Integer, Boolean> {
//       // @SuppressLint("StaticFieldLeak") @Override protected Boolean doInBackground(Void... voids) { return sntpClient.requestTime(ipServer,3000); }
//        //@SuppressLint("StaticFieldLeak") @Override protected void onPostExecute(Boolean result) { if (result) { @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
//    } } }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        try (FileChannel source = new FileInputStream(sourceFile).getChannel();
             FileChannel destination = new FileOutputStream(destFile).getChannel()) {
            destination.transferFrom(source, 0, source.size());
        }
    }

    @SuppressLint("NonConstantResourceId")
    @SneakyThrows
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.record_list_btn: //обработчик нажатия на кнопку "список записей"
                if(isRecording){ /* Выплывающее окно, если нажали не нажав, что сессия измерений закончилась, сразу хотим посомтреть список записей с микрофона */
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());
                    alertDialog.setPositiveButton("Да", (dialog, which) -> {
                        writeWorkbook(wb); //сохраняем данных в data.xls
                        navController.navigate(R.id.action_main_window_fragment_to_microphone_records_fragment); //переход на другой фрагмент
                        isRecording = false;
                    });
                    alertDialog.setNegativeButton("Нет", null);
                    alertDialog.setTitle("Идет сбор данных");
                    alertDialog.setMessage("Остановить сбор данных?");
                    alertDialog.create().show();
                } else {
                    navController.navigate(R.id.action_main_window_fragment_to_microphone_records_fragment);
                }
                break;
            case R.id.toggleButton:
                onToggleChangeClientOrServerClicked(v);
                break;
            case R.id.toggleStart:
                onTogStartReceiveOrRequestClicked(v);
                break;
            case R.id.record_btn: //обработчик нажатия на кнопку "старт/стоп"
                if(!isRecording) {  //если нажата кнопка старт
                    filenameText.setText("Сессия измерений начнется после превышения звукового порога");
                    record_button.setText("Ожидание");
                    record_button.setBackgroundColor(Color.parseColor("#C176EF"));
                    latitude.setText("нет данных");
                    longitude.setText("нет данных");
                    nowtime.setText("нет данных");
                    altitude.setText("нет данных");
                    offset.setText("");
                    delay.setText("");
                    realTimeOfSever.setText("");
                    String path = requireActivity().getExternalFilesDir(null).getAbsolutePath(); //путь к data.xls
                    path = path.substring(0, path.length() - 5);
                    file = new File(path + "Location_data.xls");
                    wb = readWorkbook(); //загружаем из памяти data.xls
                    recordStart(); //заканчиваем запись с аудирокордера
                    readStart(); //заканчиваем чтение с аудирокордера
                }
                else //если нажата кнопка стоп
                {
                    postByRetrofit();
                    writeWorkbook(wb); //сохраняем изменения в data.xls
                    stopRecording(); //заканчиваем запись медиарекордера сохраняем данные
                    record_button.setBackgroundColor(Color.parseColor("#9B1EE9"));
                    record_button.setText("Старт");
                    isRecording = false;
                }
                break;
        }
    }

    /* Проверка разрешения на использование микрофона */
    private boolean checkPermissionsAudio_SD() {
        int external_storage_res = ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int audio_res = ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.RECORD_AUDIO);
        return external_storage_res == PackageManager.PERMISSION_GRANTED && audio_res == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("SetTextI18n")
    private void startRecording() {
        timer.setBase(SystemClock.elapsedRealtime()); //запуск хронометра
        timer.start();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss"); //узнаем текущую дату
        Date now = new Date();
        sheet = wb.createSheet("File_" + formatter.format(now)); //cоздаем новый лист в таблице для новой сессии измерений
        HSSFRow row = sheet.createRow(0); //заполняем первую строку
        row.createCell(0).setCellValue("Широта");
        row.createCell(1).setCellValue("Долгота");
        row.createCell(2).setCellValue("Высота над уровнем моря");
        row.createCell(3).setCellValue("Время");
        row.createCell(4).setCellValue("offset");
        rowNum = 1;
        recordFileName = "File_" + formatter.format(now);
        recordFile = recordFileName + ".mp4";
        //настройка медиарекордера
        mediaRecorder = new MediaRecorder();
        String recordPath = requireActivity().getExternalFilesDir("/").getAbsolutePath(); //путь для сохранения медиа
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(recordPath + "/" + recordFile);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaRecorder.start();
        filenameText.setText("Идет сбор данных в файл : " + recordFileName);
    }
    //окончание записи с микрофона
    @SuppressLint("SetTextI18n")
    private void stopRecording() {
        timer.stop();
        filenameText.setText("Сессия окончена, файл сохранен : " + recordFileName);
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
        }
    }
    //запуск аудиорекордера
    public void recordStart() {
        audioRecord.startRecording();
        //int recordingState = audioRecord.getRecordingState();
    }
    //остановка аудиорекордера
    public void recordStop() {
        audioRecord.stop();
    }

    //запуск функции соединения с сервером времен  в отдельном потоке
    private final Runnable timeUpdaterRunnable = new Runnable() {
        @SneakyThrows
        public void run() {
            AlarmManager alarmManager= (AlarmManager)getContext().getSystemService(Activity.ALARM_SERVICE);
            alarmManager.setTime(getTimeFromNtpServer(String.valueOf(host_server)));
            handler.postDelayed(this, 60000);
        }
    };

//    private final Runnable realTimeOfSeverUpdaterRun = new Runnable() {
//        @SneakyThrows
//        public void startClientOnlineExchange() throws IOException {
//            timeRequestDateServer = System.currentTimeMillis();
//            Socket soc=new Socket(host_server.getText().toString(),Integer.parseInt(portOfServer));
//            BufferedReader in=new BufferedReader(new InputStreamReader(soc.getInputStream()));
//            timeReceiveDateServer = System.currentTimeMillis();
//            String currentTimeOfServer = in.readLine();
//            String[] timeTokens = currentTimeOfServer.split("-");
//            String requestInServer = timeTokens[0];
//            requestOutServer = timeTokens[1];
//            //offset - смещение (theta)
//            theta = ((Long.parseLong(requestInServer) - timeRequestDateServer) + (Long.parseLong(requestOutServer) - timeReceiveDateServer))/2L;
//            //delay - задержка (delta)
//            delta = (timeReceiveDateServer - timeRequestDateServer) - (Long.parseLong(requestOutServer) - Long.parseLong(requestInServer));
//            //diffTime - разница времени между сервером и дквайсом
//            long regularTimeOfServer = timeReceiveDateServer - theta - delta;
//            long regularTimeOfDevice = System.currentTimeMillis();
//            diffTime = regularTimeOfServer - regularTimeOfDevice;
//            postByRetrofit();
//            handler.postDelayed(this, 30000);
//        }
//};

    private void changeSystemTime(Long date){
        try {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMDD.HHMMSS");
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            String command = "date -s " + sdf.format(date) + "\n";
            Log.e("command",command);
            os.writeBytes(command);
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();

        } catch (InterruptedException | IOException e) {
            Toast.makeText(getContext(),e.toString(),Toast.LENGTH_SHORT).show();
        }
    }

    public long getTimeFromNtpServer(String host_server) throws Exception {
        InetAddress address = InetAddress.getByName(host_server);
        TimeInfo timeInfo = ntpudpClient.getTime(address);
        return timeInfo.getReturnTime();
    }

//    public Date getTimeFromServer(String host_server) throws Exception {
//        Socket soc=new Socket(host_server,4409);
//        BufferedReader in=new BufferedReader(new InputStreamReader(soc.getInputStream()));
//
//        String time = null;
//        try {
//            time = in.readLine();
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.i("TAG/Socket from server:",e.getLocalizedMessage());
//            Log.i("TAG/Time of Server: ", time);
//        }
//        return new Date(time);
//    }

    //чтение из аудиорекордера
    public void readStart() {
        isReading = true;
        //читаем данные с аудиорекордера в другом потоке
        new Thread(() -> {
            if (audioRecord == null)
                return;
            short[] myBuffer = new short[myBufferSize];
            int i = 0;
            while (isReading) {
                audioRecord.read(myBuffer, 0, myBufferSize); //читаем данные с аудирокордера
                noise = inputNoise.getText().toString();
                if(myBuffer[i] > Short.parseShort(noise)) {
                    //подсчитываем время сервера на данный момент
//                    if(!timeOfServerUpdateBoolean){
//                        breakStartRecording();
//                    }else {
//                        calculateTimeDetails();
                        //останавливаем аудиорекордер
                    nowTimeSignal = System.currentTimeMillis();
                        readStop();
                        recordStop();
                        //запуск потока для записи с микрофона мидиарекордера
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> {
                            startRecording();
                            record_button.setBackgroundColor(Color.RED);
                            record_button.setText("Cтоп");
                            isRecording = true;
                        });
                    //}
                }
            }
        }).start();
    }


    public void breakStartRecording(){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast.makeText(getContext(), "Время не получено!", Toast.LENGTH_LONG).show();
            record_button.setBackgroundColor(Color.parseColor("#9B1EE9"));
            record_button.setText("Старт");
            isRecording = false;
        });
    }

    public void calculateTimeDetails(){
        timeWhenSignalCome = System.currentTimeMillis();
        long timeBeforeSignal = timeWhenSignalCome - currentSysTime;
        long trueTimeOfServer = timeBeforeSignal + regularTimeOfServer;
        nowtime.setText(sdf.format(timeWhenSignalCome));
        realTimeOfSever.setText(sdf.format(trueTimeOfServer));
        offset.setText(String.valueOf(theta));
        delay.setText(String.valueOf(delta));
    }

//    public void refreshButtons() {
//        boolean adminState = dpm.isAdminActive(deviceAdmin);
//        //boolean kioskState = EnterpriseDeviceManager.getInstance(this).getKioskMode().isKioskModeEnabled();
//        if (!adminState) { toggle_admin_btn.setText(getString(R.string.admin_on));
//        } else { toggle_admin_btn.setText(getString(R.string.admin_off)); }
//    }
//    private void toggleAdmin() {
//        DevicePolicyManager dpm = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
//        boolean adminActive = dpm.isAdminActive(deviceAdmin);
//
//        if (adminActive) { // If Admin is activated
//            Logger.getLogger(getResources().getString(R.string.deactivating_admin));
//            // Deactivate this application as device administrator
//            dpm.removeActiveAdmin(new ComponentName(requireContext(), SampleAdminReceiver.class));
//        } else { // If Admin is deactivated
//            Logger.getLogger(getResources().getString(R.string.activating_admin));
//            // Ask the user to add a new device administrator to the system
//            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
//            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
//            //intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Your boss told you to do this");
//            // Start the add device admin activity
//            startActivityForResult(intent, DEVICE_ADMIN_ADD_RESULT_ENABLE);
//        }
//    }
//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == DEVICE_ADMIN_ADD_RESULT_ENABLE) {
//            switch (resultCode) {
//                // End user cancels the request
//                case Activity.RESULT_CANCELED:
//                    Logger.getLogger(getResources().getString(R.string.admin_cancelled));
//                    break;
//                // End user accepts the request
//                case Activity.RESULT_OK:
//                    Logger.getLogger(getResources().getString(R.string.admin_activated));
//                    refreshButtons();
//                    break;
//            }
//        }
//    }

    public void readStop() {
        isReading = false;
    }

    //отклик на пользовательское разрешение GPS, АУДИО, SD
    @SuppressLint("NeedOnRequestPermissionsResult")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        requireActivity().onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            //проверяем дал ли пользователь разрешение на использование GPS
            case REQUEST_PERMISSION_CODE_GPS: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkGPSPermissions();
                } }
            break;
            //проверяем дал ли пользователь разрешение на использование Audio and SD
            case REQUEST_PERMISSION_CODE_AUDIO_SD: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getActivity(), "Permission Granted", Toast.LENGTH_SHORT).show(); }
                else {
                    Toast.makeText(getActivity(), "Permission Denied", Toast.LENGTH_SHORT).show();
                } }
            break;
//            case REQUEST_SET_TIME: {
//                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
//                    Toast.makeText(getActivity(), "Permission Granted", Toast.LENGTH_SHORT).show();
//                } else {
//                    Toast.makeText(getActivity(), "Permission Denied", Toast.LENGTH_SHORT).show();
//                    checkOnChangeTimePermission();
//                } }
//                break;
            default: throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }

    //проверка на GPS соединение
    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void checkGPSPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_CODE_GPS); }
        else {
            if(!flag_res_gps) {
                Toast.makeText(getActivity(), "Permission GPS Granted", Toast.LENGTH_SHORT).show();
                flag_res_gps = true; }
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 0, myLocation); }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    // функция для получения location, вызывается при изменении location в классе MyLocation
    @Override
    public void getNowLocation(Location location) {
        if(isRecording) {
            latitude.setText(String.valueOf(location.getLatitude()));
            longitude.setText(String.valueOf(location.getLongitude()));
            altitude.setText(String.valueOf(location.getAltitude()));
            HSSFRow row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue((String) latitude.getText());
            row.createCell(1).setCellValue((String) longitude.getText());
            row.createCell(2).setCellValue((String) altitude.getText());
            row.createCell(4).setCellValue((String) nowtime.getText());
            rowNum++;
        }
    }

    //загрузка data.xls
    public HSSFWorkbook readWorkbook() {
        try {
            HSSFWorkbook wb;
            if(file.exists()) { //если data.xls уже существует
                FileInputStream fs = new FileInputStream(file);
                wb = new HSSFWorkbook(fs); //считываем ее из памяти
            }
            else { //инчае
                wb = new HSSFWorkbook(); //создаем новую
            }
            return wb;
        }
        catch (Exception e) {
            return null;
        }
    }
    //сохранение data.xls
    public void writeWorkbook(HSSFWorkbook wb) {
        try {
            FileOutputStream fileOut = new FileOutputStream(file);
            wb.write(fileOut);
            fileOut.close();
        }
        catch (Exception e) {
            //Обработка ошибки
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isReading = false;
        if (audioRecord != null) {
            audioRecord.release();
        }
    }

    public String getIpAddressDevice(){
        Context context = requireContext().getApplicationContext();
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }

    public void enableServer(){
        toggle_button.setBackgroundColor(Color.YELLOW);
        Toast.makeText(getContext(), "Теперь это сервер!", Toast.LENGTH_LONG).show();
        namePhone = nameOfPhoneView.getText().toString();
        portOfServer = portServer.getText().toString();
        host_server.setText(getIpAddressDevice());
        flag_client = false;
        flag_server = true;
    }

    public void enableClient(){
        toggle_button.setBackgroundColor(Color.CYAN);
        Toast.makeText(getContext(), "Теперь это клиент!", Toast.LENGTH_LONG).show();
        namePhone = nameOfPhoneView.getText().toString();
        portOfServer = portServer.getText().toString();
        portOfDb = portDb.getText().toString();
        host_server.setText(ipServer);
        flag_server = false;
        flag_client = true;
    }

    public void onToggleChangeClientOrServerClicked(View view) {
        boolean on = ((ToggleButton) view).isChecked();
        if (on) {
            enableServer();
        } else {
            enableClient();
        }
    }

    public void onTogStartReceiveOrRequestClicked(View view){
        boolean on = ((ToggleButton) view).isChecked();
        Handler handler = new Handler(Looper.getMainLooper());
        if (on) {
            exchangeRun.start();
            handler.post(() -> progressBar.setVisibility(View.VISIBLE));
            toggle_start.setBackgroundColor(Color.GREEN);
            if(flag_client) Toast.makeText(getContext(), "Прием начат", Toast.LENGTH_LONG).show();
            else Toast.makeText(getContext(), "Обмен начат", Toast.LENGTH_LONG).show();
        } else {
            exchangeRun.stop();
            toggle_start.setBackgroundColor(Color.GRAY);
            handler.post(() -> progressBar.setVisibility(View.INVISIBLE));
            if(flag_client) Toast.makeText(getContext(), "Прием закончен", Toast.LENGTH_LONG).show();
            else Toast.makeText(getContext(), "Обмен закончен", Toast.LENGTH_LONG).show();
        }
    }

    public class OnlineExchangeRun implements Runnable {
        Thread backgroundThread;
        public void start() {
            if( backgroundThread == null ) {
                backgroundThread = new Thread( this );
                backgroundThread.start();
            }
        }
        public void stop() {
            if( backgroundThread != null ) {
                backgroundThread.interrupt();
            }
        }
        @SneakyThrows
        public void run() {
            try {
                while( !backgroundThread.interrupted() ) {
                    onlineExchanging();
                    Thread.sleep(60000);
                }
            } catch( InterruptedException ex ) {
                ex.fillInStackTrace();
            } finally {
                backgroundThread = null;
            }
        }
        public void onlineExchanging() throws Exception{
            if (flag_server) {
                startServer();
            } else if (flag_client) {
                startClient();
            }
        }
        public void startServer() throws IOException {
            while (true) {
                ServerSocket s = new ServerSocket(Integer.parseInt(portOfServer));
                StringBuilder sb = new StringBuilder();
                Socket soc = s.accept();
                long requestIn = System.currentTimeMillis();
                DataOutputStream out = new DataOutputStream(soc.getOutputStream());
                long requestOut = System.currentTimeMillis();
                sb.append(requestIn).append("-").append(requestOut);
                out.writeBytes(sb.toString());
                out.close();
                soc.close();
            }
        }
        public void startClient() {
//            long timeRequestDateServer = System.currentTimeMillis();
//            Socket soc=new Socket(host_server.getText().toString(),Integer.parseInt(portOfServer));
//            BufferedReader in=new BufferedReader(new InputStreamReader(soc.getInputStream()));
//            timeReceiveDateServer = System.currentTimeMillis();
//            String currentTimeOfServer = in.readLine();
//            String[] timeTokens = currentTimeOfServer.split("-");
//            String requestInServer = timeTokens[0];
//            requestOutServer = timeTokens[1];
//            //offset - смещение (theta)
//            theta = ((Long.parseLong(requestInServer) - timeRequestDateServer) + (Long.parseLong(requestOutServer) - timeReceiveDateServer))/2L;
//            //delay - задержка (delta)
//            delta = (timeReceiveDateServer - timeRequestDateServer) - (Long.parseLong(requestOutServer) - Long.parseLong(requestInServer));
//            //diffTime - моментальная разница времени между сервером и дквайсом
            timeOfServerUpdateBoolean = sntpClient.requestTime(host_server.getText().toString(),Integer.parseInt(portOfServer),10000);
            theta = sntpClient.getOffsetTime();
            delta = sntpClient.getRoundTripTime();
            regularTimeOfServer = sntpClient.getNtpTime();
//            long time = TimeService.currentTimeMillis();
//            long regularTimeOfServer = timeReceiveDateServer - theta - delta;
            currentSysTime = sntpClient.getCurrentSysTime();
            diffTime = regularTimeOfServer - currentSysTime;
            //postByRetrofit();//отправка результатов на сервер базы данных
        }
    }
}

