package com.gurrrik.YaTranslate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultHttpRequestFactory;

public class TranslateActivity extends Activity {
    private Spinner langChooser;
    private String selectedTranslateDirection;
    private EditText textToTranslate;
    private TextView translatedText;
    private HttpHost yandexTranslateHost;
    private DefaultHttpRequestFactory httpRequestFactory;
    private AndroidHttpClient httpClient;

    private class LangChooserOnItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
        {
            selectedTranslateDirection = parent.getItemAtPosition(pos).toString();
            Log.d("LangChooserOnItemSelectedListener", selectedTranslateDirection);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {

        }
    }

    private class BackgroundLangsFetch extends AsyncTask<Void, Void, ArrayList<String>> {
        private Context context;

//        private String getStringFromInputStream(InputStream is)
//        {
//            BufferedReader br = null;
//            StringBuilder sb = new StringBuilder();
//
//            String line;
//            try {
//
//                br = new BufferedReader(new InputStreamReader(is));
//                while ((line = br.readLine()) != null) {
//                    sb.append(line);
//                }
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                if (br != null) {
//                    try {
//                        br.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//
//            return sb.toString();
//
//        }

        BackgroundLangsFetch(Context c) {
            context = c;
        }

        @Override
        protected ArrayList<String> doInBackground(Void... params) {
            ArrayList<String> langs = new ArrayList<String>();

            try {
                String getLangsUri = getString(R.string.yandex_translate_api_request)
                                   + getString(R.string.get_langs_request_url);
                HttpRequest getLangsHttpRequest = httpRequestFactory.newHttpRequest("GET", getLangsUri);
                HttpResponse getLangsResponse = httpClient.execute(yandexTranslateHost, getLangsHttpRequest);
                HttpEntity e = getLangsResponse.getEntity();
                InputStream is = e.getContent();

                JsonReader jReader = new JsonReader(new InputStreamReader(is, "UTF-8"));
                jReader.beginObject();
                while (jReader.hasNext()) {
                    if (jReader.nextName().equals("dirs")) {
                        jReader.beginArray();
                        while (jReader.hasNext()) {
                            String lang = jReader.nextString();
                            langs.add(lang);
                            Log.d("BackgroundLangsFetch", lang);
                        }
                        jReader.endArray();
                    } else {
                        jReader.skipValue();
                    }
                }
                jReader.endObject();

                e.consumeContent();
            } catch (Exception e) {
                langs.add("Error retrieving languages!");
                return langs;
            }

            return langs;
        }

        @Override
        protected void onPostExecute(ArrayList<String> result) {
            langChooser.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, result));
            langChooser.setOnItemSelectedListener(new LangChooserOnItemSelectedListener());
        }
    }

    private class BackgroundTranslation extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... forTranslation) {
            String translatedText = "";

            try {
                String translateUri = getString(R.string.yandex_translate_api_request)
                                    + getString(R.string.translate_request_url)
                                    + "?lang=" + selectedTranslateDirection
                                    + "&text=" + URLEncoder.encode(textToTranslate.getText().toString(), "UTF-8");
                HttpRequest translateHttpRequest = httpRequestFactory.newHttpRequest("GET", translateUri);
                HttpResponse translateResponse = httpClient.execute(yandexTranslateHost, translateHttpRequest);
                HttpEntity e = translateResponse.getEntity();
                InputStream is = e.getContent();

                JsonReader jReader = new JsonReader(new InputStreamReader(is, "UTF-8"));
                jReader.beginObject();
                while (jReader.hasNext()) {
                    if (jReader.nextName().equals("text")) {
                        jReader.beginArray();
                        while (jReader.hasNext()) {
                            String text = jReader.nextString();
                            translatedText = translatedText + text + "\n";
                            Log.d("BackgroundTranslation", text);
                        }
                        jReader.endArray();
                    } else {
                        jReader.skipValue();
                    }
                }
                jReader.endObject();
            } catch (Exception e) {
                return "Error translating text!";
            }

            return translatedText;
        }

        @Override
        protected void onPostExecute(String result) {
            translatedText.setText(result);
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        langChooser = (Spinner)findViewById(R.id.langChooserSpinner);
        textToTranslate = (EditText)findViewById(R.id.textToTranslate);
        translatedText = (TextView)findViewById(R.id.translatedTextView);

        try {
            yandexTranslateHost = new HttpHost(getString(R.string.yandex_translate_host));
        } catch (Exception e) {
            finish();
        }

        ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected()) {
            Log.d("onCreate", "No connection");
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setCancelable(false);
            ad.setTitle(getString(R.string.network_error_title));
            ad.setMessage(getString(R.string.network_error));
            ad.setButton(DialogInterface.BUTTON_POSITIVE, "Exit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            ad.show();
        } else {
            Log.d("onCreate", "Existing connection");
            httpClient = AndroidHttpClient.newInstance(getString(R.string.user_agent));
            httpRequestFactory = new DefaultHttpRequestFactory();

            new BackgroundLangsFetch(this).execute();
        }
    }

    /**
     * Translate button clicked
     */
    public void onTranslateClicked(View v) {
        new BackgroundTranslation().execute(textToTranslate.getText().toString());
    }
}
