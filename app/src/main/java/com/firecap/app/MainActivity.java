package com.firecap.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final int BG=0xff10151e, TEAL=0xff14b9b8, MUTED=0xffa9bdc9;
 private EditText ip;
 private TextView status, deviceLabel;
 private ImageView preview;
 private Button captureButton;
 private byte[] latest;
 private AdbClient connection;
 private String connectedHost;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<String> deviceNames=new ArrayList<>(), deviceIps=new ArrayList<>();
 private final ArrayList<String> galleryUris=new ArrayList<>();
 private final ArrayList<String> galleryNames=new ArrayList<>();
 private int quality=82;
 private boolean busy=false;

 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(22);return d;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(-1);b.setAllCaps(false);b.setTextSize(16);b.setBackground(shape(0xff087e83));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(7),0,dp(7));b.setLayoutParams(p);return b;}
 private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
 private android.content.SharedPreferences prefs(){return getPreferences(0);}
 private void tell(String s){runOnUiThread(()->{status.setText(s);status.setTextColor(s.startsWith("Connected")||s.startsWith("Saved")?TEAL:MUTED);});}
 private void setBusy(boolean v){busy=v;runOnUiThread(()->captureButton.setEnabled(!v));}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  load();ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(24),dp(20),dp(32));root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
  TextView title=text("▣  FIRECAP",32,TEAL);root.addView(title);
  TextView sub=text("Fire TV screenshot studio  •  JPG",14,MUTED);sub.setPadding(0,dp(5),0,dp(24));root.addView(sub);
  deviceLabel=text("DEVICE",12,MUTED);root.addView(deviceLabel);
  ip=new EditText(this);ip.setSingleLine(true);ip.setTextColor(-1);ip.setHintTextColor(MUTED);ip.setHint("Fire TV IP address");ip.setText(prefs().getString("ip",""));ip.setPadding(dp(14),dp(12),dp(14),dp(12));ip.setBackground(shape(0xff223140));root.addView(ip);
  Button saveDevice=button("＋  Save this Fire TV");root.addView(saveDevice);saveDevice.setOnClickListener(v->saveCurrentDevice());Button devices=button("Select / manage saved devices");root.addView(devices);devices.setOnClickListener(v->deviceDialog());
  status=text("Disconnected • Enable ADB debugging on Fire TV",14,MUTED);status.setPadding(0,dp(12),0,dp(15));root.addView(status);
  Button connect=button("Connect / Reconnect");root.addView(connect);connect.setOnClickListener(v->connect());
  captureButton=button("●  Capture & auto-save JPG");root.addView(captureButton);captureButton.setTextSize(18);captureButton.setOnClickListener(v->capture());
  preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(dp(185));preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(shape(0xff1e2937));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(220));pp.setMargins(0,dp(12),0,dp(12));root.addView(preview,pp);
  Button gallery=button("Screenshot gallery");root.addView(gallery);gallery.setOnClickListener(v->showGallery());
  Button settings=button("JPG quality: "+quality+"%");root.addView(settings);settings.setOnClickListener(v->qualityDialog(settings));
  if(!ip.getText().toString().trim().isEmpty())connect();
 }
 private void load(){quality=prefs().getInt("quality",82);try{JSONArray a=new JSONArray(prefs().getString("devices","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);deviceNames.add(o.getString("name"));deviceIps.add(o.getString("ip"));}}catch(Exception ignored){}
  try{JSONArray a=new JSONArray(prefs().getString("gallery","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);galleryUris.add(o.getString("uri"));galleryNames.add(o.getString("name"));}}catch(Exception ignored){}
 }
 private void saveDevices(){JSONArray a=new JSONArray();for(int i=0;i<deviceIps.size();i++){JSONObject o=new JSONObject();try{o.put("name",deviceNames.get(i));o.put("ip",deviceIps.get(i));a.put(o);}catch(Exception ignored){}}prefs().edit().putString("devices",a.toString()).apply();}
 private void saveGallery(){JSONArray a=new JSONArray();for(int i=0;i<galleryUris.size();i++){JSONObject o=new JSONObject();try{o.put("uri",galleryUris.get(i));o.put("name",galleryNames.get(i));a.put(o);}catch(Exception ignored){}}prefs().edit().putString("gallery",a.toString()).apply();}
 private void saveCurrentDevice(){String address=ip.getText().toString().trim();if(address.isEmpty()){tell("Enter the Fire TV IP address first");return;}EditText name=new EditText(this);name.setSingleLine(true);name.setHint("e.g. Living Room Fire TV");for(int i=0;i<deviceIps.size();i++)if(address.equals(deviceIps.get(i))){name.setText(deviceNames.get(i));break;}new AlertDialog.Builder(this).setTitle("Save Fire TV device").setMessage("Save "+address+" for quick access").setView(name).setPositiveButton("Save device",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())n="Fire TV "+address;int existing=deviceIps.indexOf(address);if(existing>=0){deviceNames.set(existing,n);}else{deviceIps.add(address);deviceNames.add(n);}saveDevices();tell("Saved device: "+n);}).setNegativeButton("Cancel",null).show();}
 private void deviceDialog(){String[] items=new String[deviceIps.size()+1];for(int i=0;i<deviceIps.size();i++)items[i]=deviceNames.get(i)+"  ("+deviceIps.get(i)+")";items[items.length-1]="+ Save current IP as device";
  new AlertDialog.Builder(this).setTitle("Saved Fire TV devices").setItems(items,(d,which)->{if(which==deviceIps.size()){String address=ip.getText().toString().trim();if(address.isEmpty()){tell("Enter an IP address first");return;}EditText name=new EditText(this);name.setSingleLine(true);name.setHint("e.g. Living Room Fire TV");new AlertDialog.Builder(this).setTitle("Name this device").setView(name).setPositiveButton("Save",(dd,w)->{String n=name.getText().toString().trim();deviceIps.add(address);deviceNames.add(n.isEmpty()?address:n);saveDevices();tell("Saved device "+(n.isEmpty()?address:n));}).setNegativeButton("Cancel",null).show();}else{final int idx=which;new AlertDialog.Builder(this).setTitle(deviceNames.get(idx)).setItems(new String[]{"Connect to this device","Delete saved device"},(dd,action)->{if(action==0){ip.setText(deviceIps.get(idx));connect();}else{deviceIps.remove(idx);deviceNames.remove(idx);saveDevices();tell("Device removed");}}).show();}}).setNegativeButton("Close",null).show();
 }
 private void qualityDialog(Button b){String[] opts={"60% • Smallest files","75% • Compact","85% • Balanced","95% • Highest quality"};int[] values={60,75,85,95};new AlertDialog.Builder(this).setTitle("JPG quality").setSingleChoiceItems(opts,quality==60?0:quality==75?1:quality==95?3:2,(d,n)->{quality=values[n];prefs().edit().putInt("quality",quality).apply();b.setText("JPG quality: "+quality+"%");d.dismiss();}).setNegativeButton("Cancel",null).show();}
 private KeyPair key()throws Exception{android.content.SharedPreferences p=prefs();String priv=p.getString("priv",null),pub=p.getString("pub",null);if(priv==null||pub==null){KeyPair kp=AdbClient.newKey();p.edit().putString("priv",android.util.Base64.encodeToString(kp.getPrivate().getEncoded(),2)).putString("pub",android.util.Base64.encodeToString(kp.getPublic().getEncoded(),2)).apply();return kp;}KeyFactory f=KeyFactory.getInstance("RSA");return new KeyPair(f.generatePublic(new X509EncodedKeySpec(android.util.Base64.decode(pub,2))),f.generatePrivate(new PKCS8EncodedKeySpec(android.util.Base64.decode(priv,2))));}
 private void ensureConnected(String host)throws Exception{if(connection!=null&&host.equals(connectedHost))return;disconnect();connection=new AdbClient(host,5555,key());connectedHost=host;}
 private void connect(){String host=ip.getText().toString().trim();if(host.isEmpty()){tell("Enter the Fire TV IP address");return;}prefs().edit().putString("ip",host).apply();tell("Connecting…");worker.execute(()->{try{ensureConnected(host);tell("Connected • "+host);}catch(Exception e){disconnect();tell("Disconnected • "+e.getMessage());}});}
 private void capture(){if(busy)return;String host=ip.getText().toString().trim();if(host.isEmpty()){tell("Enter the Fire TV IP address");return;}prefs().edit().putString("ip",host).apply();setBusy(true);tell("Capturing screenshot…");worker.execute(()->{try{ensureConnected(host);byte[] png;try{png=connection.screenshot();}catch(Exception first){disconnect();ensureConnected(host);png=connection.screenshot();}Bitmap bitmap=BitmapFactory.decodeByteArray(png,0,png.length);if(bitmap==null)throw new IOException("Could not decode screenshot");ByteArrayOutputStream output=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,output))throw new IOException("JPG conversion failed");latest=output.toByteArray();runOnUiThread(()->preview.setImageBitmap(bitmap));String name=safeName(host)+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.UK).format(new Date())+".jpg";Uri saved=saveImage(latest,name);galleryUris.add(0,saved.toString());galleryNames.add(0,name);saveGallery();tell("Saved JPG • "+(latest.length/1024)+" KB • Connected");}catch(Exception e){disconnect();tell("Disconnected • "+e.getMessage());}finally{setBusy(false);}});}
 private String safeName(String ipAddress){String n=ipAddress;for(int i=0;i<deviceIps.size();i++)if(ipAddress.equals(deviceIps.get(i))){n=deviceNames.get(i);break;}return n.replaceAll("[^a-zA-Z0-9_-]","_");}
 private Uri saveImage(byte[] data,String name)throws Exception{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FIRECAP");Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Unable to create JPG");try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Unable to open JPG");out.write(data);}catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}return uri;}
 private void showGallery(){if(galleryUris.isEmpty()){new AlertDialog.Builder(this).setTitle("Screenshot gallery").setMessage("No screenshots captured yet.").setPositiveButton("OK",null).show();return;}String[] names=galleryNames.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("FIRECAP gallery").setItems(names,(d,index)->showImage(index)).setNegativeButton("Close",null).show();}
 private void showImage(int index){Uri uri=Uri.parse(galleryUris.get(index));ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap bitmap=BitmapFactory.decodeStream(in);if(bitmap==null)throw new IOException("Image missing");image.setImageBitmap(bitmap);}catch(Exception e){tell("Cannot open image: "+e.getMessage());return;}new AlertDialog.Builder(this).setTitle(galleryNames.get(index)).setView(image).setPositiveButton("Share",(d,w)->{Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("image/jpeg");intent.putExtra(Intent.EXTRA_STREAM,uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(intent,"Share screenshot"));}).setNeutralButton("Delete",(d,w)->new AlertDialog.Builder(this).setTitle("Delete screenshot?").setMessage("This will remove the JPG from your phone.").setPositiveButton("Delete",(dd,ww)->{try{getContentResolver().delete(uri,null,null);galleryUris.remove(index);galleryNames.remove(index);saveGallery();tell("Screenshot deleted");}catch(Exception e){tell("Delete failed: "+e.getMessage());}}).setNegativeButton("Cancel",null).show()).setNegativeButton("Close",null).show();}
 private void disconnect(){if(connection!=null){try{connection.close();}catch(Exception ignored){}connection=null;connectedHost=null;}}
 @Override protected void onDestroy(){worker.execute(()->disconnect());worker.shutdown();super.onDestroy();}
}
