package com.example.homepc.addressbookdeitel;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.support.annotation.Nullable;

import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by HomePC on 18/3/2016.
 */
public class ViewContact extends AppCompatActivity {
    private static final String TAG = ViewContact.class.getName();
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private BluetoothAdapter bluetoothAdapter = null;
    private Handler handler;

    private long rowID;
    private TextView nameTextView;
    private TextView phoneTextView;
    private TextView emailTextView;
    private TextView streetTextView;
    private TextView cityTextView;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_contact);
        Toolbar toolbar = (Toolbar) findViewById(R.id.view_contact_toolbar);
        setSupportActionBar(toolbar);

        nameTextView = (TextView) findViewById(R.id.nameTextView);
        phoneTextView = (TextView) findViewById(R.id.phoneTextView);
        emailTextView = (TextView) findViewById(R.id.emailTextView);
        streetTextView = (TextView) findViewById(R.id.streetTextView);
        cityTextView = (TextView) findViewById(R.id.cityTextView);

        Bundle extras = getIntent().getExtras();
        rowID = extras.getLong(AddressBook.ROW_ID);

        //get the bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        handler = new Handler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_contact_menu, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        new LoadContactTask().execute(rowID);
    }

    //perform database query outside GUI thread
    private class LoadContactTask extends AsyncTask<Long, Object, Cursor> {

        DatabaseConnector databaseConnector = new DatabaseConnector(ViewContact.this);
        @Override
        protected Cursor doInBackground(Long... params) {
            databaseConnector.open();
            return databaseConnector.getOneContact(params[0]);
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            super.onPostExecute(cursor);

            cursor.moveToFirst();

            int nameIndex = cursor.getColumnIndex("name");
            int phoneIndex = cursor.getColumnIndex("phone");
            int emailIndex = cursor.getColumnIndex("email");
            int streetIndex = cursor.getColumnIndex("street");
            int cityIndex = cursor.getColumnIndex("city");

            nameTextView.setText(cursor.getString(nameIndex));
            phoneTextView.setText(cursor.getString(phoneIndex));
            emailTextView.setText(cursor.getString(emailIndex));
            streetTextView.setText(cursor.getString(streetIndex));
            cityTextView.setText(cursor.getString(cityIndex));

            cursor.close();
            databaseConnector.close();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.editItem:
                Intent addEditContact = new Intent(this, AddEditContact.class);

                addEditContact.putExtra(AddressBook.ROW_ID, rowID);
                addEditContact.putExtra("name", nameTextView.getText());
                addEditContact.putExtra("phone", phoneTextView.getText());
                addEditContact.putExtra("email", emailTextView.getText());
                addEditContact.putExtra("street", streetTextView.getText());
                addEditContact.putExtra("city", cityTextView.getText());
                startActivity(addEditContact);
                break;

            case R.id.deleteItem:
                deleteContact();
                break;

            case R.id.transferItem:
                if (bluetoothAdapter.isEnabled()) {
                    Intent serverIntent = new Intent(this, DeviceChooser.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    Toast.makeText(this, R.string.no_bluetooth, Toast.LENGTH_LONG).show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // delete a contact
    private void deleteContact()
    {
        // create a new AlertDialog Builder
        AlertDialog.Builder builder =
                new AlertDialog.Builder(ViewContact.this);

        builder.setTitle(R.string.confirmTitle); // title bar string
        builder.setMessage(R.string.confirmMessage); // message to display

        // provide an OK button that simply dismisses the dialog
        builder.setPositiveButton(R.string.button_delete,
                new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int button)
                    {
                        final DatabaseConnector databaseConnector =
                                new DatabaseConnector(ViewContact.this);

                        // create an AsyncTask that deletes the contact in another
                        // thread, then calls finish after the deletion
                        AsyncTask<Long, Object, Object> deleteTask =
                                new AsyncTask<Long, Object, Object>()
                                {
                                    @Override
                                    protected Object doInBackground(Long... params)
                                    {
                                        databaseConnector.deleteContact(params[0]);
                                        return null;
                                    } // end method doInBackground

                                    @Override
                                    protected void onPostExecute(Object result)
                                    {
                                        finish(); // return to the AddressBook Activity
                                    } // end method onPostExecute
                                }; // end new AsyncTask

                        // execute the AsyncTask to delete contact at rowID
                        deleteTask.execute(new Long[] { rowID });
                    } // end method onClick
                } // end anonymous inner class
        ); // end call to method setPositiveButton

        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show(); // display the Dialog
    } // end method deleteContact

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK) {
            new SendContactTask().execute(new String[] {
                    data.getExtras().getString(DeviceChooser.DEVICE_ADDRESS)
            });
        } else {
            Toast.makeText(this, R.string.connection_error, Toast.LENGTH_LONG).show();
        }
    }

    private class SendContactTask extends AsyncTask<String,Object,Object> {

        @Override
        protected Object doInBackground(String... params) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(params[0]);

            BluetoothSocket bluetoothSocket = null;

            try {
                AddressBook.displayToastViaHandler(ViewContact.this, handler,
                        R.string.sending_contact);

                bluetoothSocket = device.createRfcommSocketToServiceRecord(
                        AddressBook.MY_UUID
                );

                bluetoothSocket.connect();

                OutputStream outputStream = bluetoothSocket.getOutputStream();

                //create representative of contact
                final JSONObject contact = new JSONObject();
                contact.put("name", nameTextView.getText().toString());
                contact.put("phone", phoneTextView.getText().toString());
                contact.put("email", emailTextView.getText().toString());
                contact.put("street", streetTextView.getText().toString());
                contact.put("city", cityTextView.getText().toString());

                outputStream.write(contact.toString().getBytes());
                outputStream.flush();
                AddressBook.displayToastViaHandler(ViewContact.this, handler,
                R.string.contact_sent);
            } catch (IOException e) {
                e.printStackTrace();
                AddressBook.displayToastViaHandler(ViewContact.this, handler,
                        R.string.transfer_failed);
            } catch (JSONException e) {
                e.printStackTrace();
                AddressBook.displayToastViaHandler(ViewContact.this, handler,
                        R.string.transfer_failed);
            } finally {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
                bluetoothSocket = null;
            }

            return null;
        }
    }
}
