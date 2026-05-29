/* Copyright 2013-2016 Wilco Baan Hofman <wilco@baanhofman.nl>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.eurofurence.wifisetup;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/* import android.util.Base64; */
// API level 18 and up
enum Profile {
    PROFILE_UNFILTERED,
    PROFILE_SITEONLY,
    PROFILE_PROTECTME,
    PROFILE_SPECIAL
}

public class WifiSetup extends AppCompatActivity {
    protected static final int SHOW_PREFERENCES = 0;
    // FIXME This should be a configuration setting somehow
    private static final String INT_EAP = "eap";
    private static final String INT_PHASE2 = "phase2";
    private static final String INT_ENGINE = "engine";
    private static final String INT_ENGINE_ID = "engine_id";
    private static final String INT_CLIENT_CERT = "client_cert";
    private static final String INT_CA_CERT = "ca_cert";
    private static final String INT_PRIVATE_KEY = "private_key";
    private static final String INT_PRIVATE_KEY_ID = "key_id";
    private static final String INT_SUBJECT_MATCH = "subject_match";
    private static final String INT_ALTSUBJECT_MATCH = "altsubject_match";
    private static final String INT_PASSWORD = "password";
    private static final String INT_IDENTITY = "identity";
    private static final String INT_ANONYMOUS_IDENTITY = "anonymous_identity";
    private static final String INT_ENTERPRISEFIELD_NAME = "android.net.wifi.WifiConfiguration$EnterpriseField";
    // Because android.security.Credentials cannot be resolved...
    private static final String INT_KEYSTORE_URI = "keystore://";
    private static final String INT_CA_PREFIX = INT_KEYSTORE_URI + "CACERT_";
    private static final String INT_PRIVATE_KEY_PREFIX = INT_KEYSTORE_URI + "USRPKEY_";
    private static final String INT_PRIVATE_KEY_ID_PREFIX = "USRPKEY_";
    private static final String INT_CLIENT_CERT_PREFIX = INT_KEYSTORE_URI + "USRCERT_";
    private Handler mHandler = new Handler();
    private EditText wpa_identity;
    private EditText wpa_password;
    private CheckBox check5g;
    private Button btn;
    private String subject_match;
    private String altsubject_match;

    private String realm;
    private String ssid;
    private boolean busy = false;
    private Toast toast = null;
    private int logoclicks = 0;
    private String identity;
    private String password;
    private ViewFlipper flipper;
    Profile selected_profile;


    static String removeQuotes(String str) {
        int len = str.length();
        if ((len > 1) && (str.charAt(0) == '"') && (str.charAt(len - 1) == '"')) {
            return str.substring(1, len - 1);
        }
        return str;
    }

    static String surroundWithQuotes(String string) {
        return "\"" + string + "\"";
    }

    private void toastText(final String text) {
        if (toast != null)
            toast.cancel();
        toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
        toast.show();
    }

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logon);

        flipper = findViewById(R.id.viewflipper);
        wpa_identity = findViewById(R.id.wpa_identity);
        wpa_password = findViewById(R.id.wpa_password);

        getSupportActionBar().show();
        ViewCompat.setOnApplyWindowInsetsListener(flipper, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            mlp.leftMargin = insets.left;
            mlp.bottomMargin = insets.bottom;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });

        Spinner spinner = findViewById(R.id.profile);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position,
                                       long id) {
                View logindata = findViewById(R.id.logindata);
                logindata.setVisibility(View.INVISIBLE);
                switch ((int) id) {
                    case 0:
                        selected_profile = Profile.PROFILE_PROTECTME;
                        toastText("You don't trust anyone? Or maybe not your device?");
                        break;
                    case 1:
                        selected_profile = Profile.PROFILE_UNFILTERED;
                        toastText("Don't filter me!");
                        break;

                    case 2:
                        selected_profile = Profile.PROFILE_SITEONLY;
                        toastText("You trust people on-site more than the internet! Thank you!");
                        break;
                    case 3:
                        selected_profile = Profile.PROFILE_SPECIAL;
                        logindata.setVisibility(View.VISIBLE);
                        toastText("Hi fellow special person!");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        btn = findViewById(R.id.button1);
        if (btn == null)
            throw new RuntimeException("button1 not found. Odd");
        btn.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View _v) {
                if (busy) {
                    return;
                }
                busy = true;
                _v.setClickable(false);

                // Most of this stuff runs in the background
                Thread t = new Thread() {

                    @Override
                    public void run() {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 18) {
                                saveWifiConfig();
                                resultStatus(true, "You should now have a wifi connection entry with correct security settings and certificate verification.\n\nMake sure to actually use it!");
                            } else {
                                throw new RuntimeException("What version is this?! API Mismatch");
                            }
                        } catch (RuntimeException e) {
                            resultStatus(false, "Something went wrong: " + e.getMessage());
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        busy = false;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                btn.setClickable(true);
                            }
                        });
                    }
                };
                t.start();

            }
        });

    }

    private void saveWifiConfig() {
        ssid = "Eurofurence";
        subject_match = "/CN=radius.eurofurence.org";
        altsubject_match = "DNS:radius.eurofurence.org";

        realm = "";
        switch (selected_profile) {
            case PROFILE_UNFILTERED:
                identity = "public";
                password = "public";
                break;
            case PROFILE_SITEONLY:
                identity = "event";
                password = "event";
                break;
            case PROFILE_PROTECTME:
                identity = "eurofurence";
                password = "eurofurence";
                break;
            case PROFILE_SPECIAL:
                identity = wpa_identity.getText().toString();
                password = wpa_password.getText().toString();
                break;
        }
        StoreWifiProfile(ssid, subject_match, altsubject_match, identity, password);
    }

    void StoreWifiProfile(String ssid, String subject_match, String altsubject_match, String identity, String password) {
        // Enterprise Settings
        HashMap<String, String> configMap = new HashMap<>();
        configMap.put(INT_SUBJECT_MATCH, subject_match);
        configMap.put(INT_ALTSUBJECT_MATCH, altsubject_match);
        configMap.put(INT_ANONYMOUS_IDENTITY, "anonymous");
        configMap.put(INT_IDENTITY, identity);
        configMap.put(INT_PASSWORD, password);
        configMap.put(INT_EAP, "TTLS");
        configMap.put(INT_PHASE2, "auth=PAP");
        configMap.put(INT_ENGINE, "0");

        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null) {
            return;
        }
        WifiConfiguration currentConfig = new WifiConfiguration();

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {

                WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        .setWpa2EnterpriseConfig(applyAndroid43EnterpriseSettings(configMap)).build();
                wifiManager.addNetworkSuggestions(Arrays.asList(suggestion));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        wifiManager.setWifiEnabled(true);


        List<WifiConfiguration> configs = null;
        for (int i = 0; i < 10 && configs == null; i++) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            configs = wifiManager.getConfiguredNetworks();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Do nothing ;-)
            }
        }
        // Use the existing ssid profile if it exists.
        boolean ssidExists = false;
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.SSID.equals(surroundWithQuotes(ssid))) {
                    currentConfig = config;
                    ssidExists = true;
                    break;
                }
            }
        }
        // This sets the CA certificate.
        currentConfig.enterpriseConfig = applyAndroid43EnterpriseSettings(configMap);

        // General (old) config settings
        currentConfig.SSID = surroundWithQuotes(ssid);
        currentConfig.hiddenSSID = false;
        currentConfig.priority = 40;
        currentConfig.status = WifiConfiguration.Status.DISABLED;

        currentConfig.allowedKeyManagement.clear();
        currentConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);

        // GroupCiphers (Allow most ciphers)
        currentConfig.allowedGroupCiphers.clear();
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);


        // PairwiseCiphers (CCMP = WPA2 only)
        currentConfig.allowedPairwiseCiphers.clear();
        currentConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

        // Authentication Algorithms (OPEN)
        currentConfig.allowedAuthAlgorithms.clear();
        currentConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

        // Protocols (RSN/WPA2 only)
        currentConfig.allowedProtocols.clear();
        currentConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);


        if (!ssidExists) {
            int networkId = wifiManager.addNetwork(currentConfig);
            wifiManager.enableNetwork(networkId, false);
        } else {
            wifiManager.updateNetwork(currentConfig);
            wifiManager.enableNetwork(currentConfig.networkId, false);
        }
        wifiManager.saveConfiguration();

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private WifiEnterpriseConfig applyAndroid43EnterpriseSettings(HashMap<String, String> configMap) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream in = getResources().openRawResource(R.raw.cacert);
            // InputStream in = new ByteArrayInputStream(Base64.decode(ca.replaceAll("-----(BEGIN|END) CERTIFICATE-----", ""), 0));
            X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(in);

            WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
            enterpriseConfig.setPhase2Method(Phase2.PAP);
            enterpriseConfig.setAnonymousIdentity(configMap.get(INT_ANONYMOUS_IDENTITY));
            enterpriseConfig.setEapMethod(Eap.TTLS);

            enterpriseConfig.setCaCertificate(caCert);
            enterpriseConfig.setIdentity(identity);
            enterpriseConfig.setPassword(password);
            enterpriseConfig.setSubjectMatch(configMap.get(INT_SUBJECT_MATCH));
            enterpriseConfig.setAltSubjectMatch(configMap.get(INT_ALTSUBJECT_MATCH));

            return enterpriseConfig;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Builder builder = new AlertDialog.Builder(this);
        switch (item.getItemId()) {
            case R.id.about:
                PackageInfo pi;
                try {
                    pi = getPackageManager().getPackageInfo(getClass().getPackage().getName(), 0);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                // Set the custom layout
                LayoutInflater inflater = getLayoutInflater();
                View customLayout = inflater.inflate(R.layout.about, null);
                builder.setView(customLayout);

                Button source_button = customLayout.findViewById(R.id.about_source_code_button);

                source_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.about_source_url)));
                        startActivity(intent);
                    } });

                Button import_button = customLayout.findViewById(R.id.about_imprint_button);

                import_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.about_imprint_url)));
                        startActivity(intent);
                    } });

                TextView version_textview = customLayout.findViewById(R.id.about_version_id);
                version_textview.setText(pi.packageName + "\n" + pi.versionName + "(" + pi.versionCode + ")");



                builder.setPositiveButton("Close", null);
                builder.show();

                return true;
            case R.id.exit:
                System.exit(0);
        }
        return false;
    }

    /* Update the status in the main thread */
    protected void resultStatus(final boolean success, final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView res_title = findViewById(R.id.resulttitle);
                TextView res_text = findViewById(R.id.result);

                System.out.println(text);
                res_text.setText(text);
                if (success)
                    res_title.setText("Success!");
                else
                    res_title.setText("ERROR!");

                if (toast != null)
                    toast.cancel();
				/* toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_LONG);
				toast.show(); */
                flipper.showNext();
            }
        });
    }
}
