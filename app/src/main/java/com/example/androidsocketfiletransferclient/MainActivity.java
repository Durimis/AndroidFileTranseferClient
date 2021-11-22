package com.example.androidsocketfiletransferclient;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Environment;

import com.example.androidsocketfiletransferclient.model.Message;
import com.example.androidsocketfiletransferclient.model.MessagesListAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import functions.Deflate;
import org.apache.commons.io.FileUtils;
import functions.AesEncrypt;

public class MainActivity extends Activity {

	EditText editTextIpAddress, editTextMesazhi;
	Button buttonLidhu;
	TextView editTextPort;
	Button buttonZgjidhFajllin;
	File fajlliZgjedhur, fajlliPerTransfer;
	String emriFajllitZgjedhur;
	ListView listViewMesazhet;
	private MessagesListAdapter adapter;
	private List<Message> listMessages;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		editTextIpAddress = (EditText) findViewById(R.id.editTextIpAddress);
		editTextMesazhi = (EditText) findViewById(R.id.editTextMesazhi);
		editTextPort = (TextView) findViewById(R.id.editTextPort);

		buttonLidhu = (Button) findViewById(R.id.buttonLidhuDheDergo);
		buttonZgjidhFajllin = (Button) findViewById(R.id.buttonZgjidhFajllin);
		listViewMesazhet = (ListView) findViewById(R.id.listViewMesazhet);
		listMessages = new ArrayList<Message>();


		adapter = new MessagesListAdapter(this, listMessages);
		listViewMesazhet.setAdapter(adapter);
		
		buttonLidhu.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				ClientThread clientThread =
						new ClientThread(
								editTextIpAddress.getText().toString(),
								Integer.parseInt(editTextPort.getText().toString()));


				clientThread.start();
			}
		});
		buttonZgjidhFajllin.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*"), "Zgjidh  fajllin"), 1);
			}
		});


	}

	private class ClientThread extends Thread {
		String dstAdresa;
		int dstPorti;

		ClientThread(String address, int port) {
			dstAdresa = address;
			dstPorti = port;
		}

		@Override
		public void run() {
			Socket socket = null;

			try {
				socket = new Socket(dstAdresa, dstPorti);
				ServerThread serverThread = new ServerThread(socket);
				serverThread.start();
				
			} catch (IOException e) {

				e.printStackTrace();
				
				final String eMsg = "Dishka shkoi gabim: " + e.getMessage();
				MainActivity.this.runOnUiThread(new Runnable() {

					@Override
					public void run() {
						Toast.makeText(MainActivity.this, 
								eMsg, 
								Toast.LENGTH_LONG).show();
					}});
				
			}
		}
	}



	public class ServerThread extends Thread {
		Socket socket;

		ServerThread(Socket socket){
			this.socket= socket;
		}

		@Override
		public void run() {
			pergaditFajllinPerKompresim();
			kompresoFajllin(fajlliZgjedhur, "deflate", fajlliPerTransfer);

			byte[] bytes = new byte[(int) fajlliPerTransfer.length()];
			BufferedInputStream bis;
			try {
				bis = new BufferedInputStream(new FileInputStream(fajlliPerTransfer));
				bis.read(bytes, 0, bytes.length);
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				oos.writeObject(bytes);


				JSONObject jsonMessage = new JSONObject();
				try {
					jsonMessage.put("file", true);
					jsonMessage.put("emriFajllit", emriFajllitZgjedhur);
					jsonMessage.put("algoritmi", "deflate" );
					jsonMessage.put("mesazhi",editTextMesazhi.getText().toString());

				}
				catch (JSONException e) {
					e.printStackTrace();
				}


				oos.writeUTF(merrTeEnkriptuar(jsonMessage.toString()));

				Message m = new Message("Klienti:", editTextMesazhi.getText().toString() + "\n" + "Fajlli:" + emriFajllitZgjedhur, true);
				appendMessage(m);

				oos.flush();
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

				final String msgFromServer =  ois.readUTF();

				Message ms = new Message("Serveri",merrTeDekriptuar(msgFromServer), false);
				appendMessage(ms);
				fshijFajllin(fajlliPerTransfer);
				socket.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1 && resultCode == RESULT_OK && null != data) {

			String inputFilePath = getPath(getApplicationContext(),data.getData());
			fajlliZgjedhur = new File(inputFilePath);
			if(inputFilePath != null) {
				emriFajllitZgjedhur = inputFilePath.substring(inputFilePath.lastIndexOf("/") + 1);
			}
			Toast.makeText(getApplicationContext(), "Keni zgjedhur fajllin: " + inputFilePath,Toast.LENGTH_SHORT).show();

		} else {
			Toast.makeText(this, "Asnje fajll nuk është zgjedhur", Toast.LENGTH_LONG).show();
		}

	}
	public void kompresoFajllin(File hyrjaPaKompresuar, String algoritimi, File daljaKompresuar){

		try{
			Deflate deflate = new Deflate();
			deflate.compressFromFile(hyrjaPaKompresuar, FileUtils.openOutputStream(daljaKompresuar));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {

		}
	}

	public void pergaditFajllinPerKompresim(){
		fajlliPerTransfer = new File("/storage/emulated/0/klienti/komp", emriFajllitZgjedhur +  ".dd");
	}

	public void fshijFajllin(File file){
		file.delete();
	}

	// Marrja e pathit per fajllin e zgjedhur

	public static String getPath(final Context context, final Uri uri) {

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
									   String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}


	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	private void appendMessage(final Message m) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				listMessages.add(m);

				adapter.notifyDataSetChanged();

				// Playing device's notification
				//playBeep();
			}
		});
	}

	public String merrTeEnkriptuar(String perEnkriptim){

		String seedValue = "kodi Sekret Test";
		String mesazhiEnkriptuar = "";
		try {

			mesazhiEnkriptuar = AesEncrypt.encrypt(seedValue, perEnkriptim);


		} catch (Exception e) {

			// TODO Auto-generated catch block

			e.printStackTrace();

		}

		return  mesazhiEnkriptuar;

	}
	public String merrTeDekriptuar(String perDekriptim){

		String seedValue = "kodi Sekret Test";
		String mesazhiDekriptuar = "";
		try {
			mesazhiDekriptuar = AesEncrypt.decrypt(seedValue, perDekriptim);


		} catch (Exception e) {

			// TODO Auto-generated catch block

			e.printStackTrace();

		}

		return  mesazhiDekriptuar;

	}


}
