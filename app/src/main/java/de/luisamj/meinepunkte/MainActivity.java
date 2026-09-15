package de.luisamj.meinepunkte;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private Uri photoUri;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> { if (ok) openCamera(); else Toast.makeText(this,"Kamerazugriff wurde nicht erlaubt.",Toast.LENGTH_LONG).show(); });
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> { if (ok) recognize(); else scanError("Kein Foto aufgenommen."); });

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(),"Android");
        ViewCompat.setOnApplyWindowInsetsListener(web,(view,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    public class Bridge {
        @JavascriptInterface public void scanNutrition(){ runOnUiThread(() -> { if(ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) openCamera(); else permission.launch(Manifest.permission.CAMERA); }); }
    }

    private void openCamera(){
        try {
            File f = new File(getCacheDir(),"naehrwerte.jpg");
            photoUri = FileProvider.getUriForFile(this,getPackageName()+".files",f);
            camera.launch(photoUri);
        } catch(Exception e){ scanError("Kamera konnte nicht geöffnet werden."); }
    }

    private void recognize(){
        try {
            InputImage image=InputImage.fromFilePath(this,photoUri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener(result -> parse(result.getText()))
                .addOnFailureListener(e -> scanError("Die Tabelle konnte nicht gelesen werden."));
        } catch(Exception e){ scanError("Das Foto konnte nicht verarbeitet werden."); }
    }

    private void parse(String raw){
        String text=raw.replace('\u00A0',' ').replace(',','.').replaceAll("[|]"," ");
        Double kcal=find(text,"(?i)(\\d{2,4}(?:\\.\\d+)?)\\s*kcal");
        if(kcal==null) kcal=find(text,"(?is)(?:energie|energy|brennwert).{0,100}?(\\d{2,4}(?:\\.\\d+)?)\\s*(?:kcal)?");
        Double fat=find(text,"(?is)(?:^|\\n)\\s*(?:fett|fat)\\b.{0,80}?(\\d{1,3}(?:\\.\\d+)?)\\s*g");
        if(fat==null) fat=find(text,"(?is)(?:fett|fat)\\b.{0,80}?(\\d{1,3}(?:\\.\\d+)?)");
        final String k=kcal==null?"":clean(kcal), f=fat==null?"":clean(fat);
        web.post(() -> web.evaluateJavascript("window.scanResult("+quote(k)+","+quote(f)+","+quote(raw)+")",null));
    }

    private Double find(String s,String regex){ try{Matcher m=Pattern.compile(regex).matcher(s);return m.find()?Double.parseDouble(m.group(1)):null;}catch(Exception e){return null;} }
    private String clean(double n){return String.format(Locale.US,n%1==0?"%.0f":"%.1f",n);}
    private String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","")+"\"";}
    private void scanError(String msg){ web.post(() -> web.evaluateJavascript("window.scanError("+quote(msg)+")",null)); }
    @Override public void onBackPressed(){ if(web.canGoBack())web.goBack();else super.onBackPressed(); }
}
