package com.example.homepc.addressbookdeitel;


import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

import java.util.logging.LogRecord;

import org.json.JSONException;
import org.json.JSONObject;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

public class AddressBook extends AppCompatActivity {

    public static final String ROW_ID = "row_id";
    private ListView contactListView;
    private CursorAdapter contactAdapter;

    public static final UUID MY_UUID =
            UUID.fromString("a1052349-fcdf-4d89-8d89-0bfe9d0d7cc7");
    private static String TAG = AddressBook.class.getName();
    private static final String NAME = "AddressBookBluetooth";
    private static final int ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_DISCOVERABILITY = 2;
    private BluetoothAdapter bluetoothAdapter = null;
    private boolean userAllowedBluetooth = true;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_book);
        Toolbar toolbar = (Toolbar) findViewById(R.id.activity_addressbook_toolbar);
        setSupportActionBar(toolbar);

        // contactListView = getListView();
        contactListView = (ListView) findViewById(R.id.listview);
        contactListView.setOnItemClickListener(viewContactListener);

        String[] from = new String[] {"name"};
        int[] to = new int[] {R.id.contactTextView};
        contactAdapter = new SimpleCursorAdapter(
                AddressBook.this, R.layout.contact_list_item, null, from, to, 1);
        contactListView.setAdapter(contactAdapter);
        //   setListAdapter(contactAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, ENABLE_BLUETOOTH);
        }

        new GetContactsTask().execute((Object[]) null);

    }

    @Override
    protected void onStop() {
        Cursor cursor = contactAdapter.getCursor();

        if (cursor != null) cursor.deactivate();

        contactAdapter.changeCursor(null);
        super.onStop();

    }

    private class GetContactsTask extends AsyncTask<Object, Object, Cursor> {

        DatabaseConnector databaseConnector = new DatabaseConnector(AddressBook.this);

        @Override
        protected Cursor doInBackground(Object... params) {
            databaseConnector.open();
            return databaseConnector.getAllContacts();
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            super.onPostExecute(cursor);
            contactAdapter.changeCursor(cursor);
            databaseConnector.close();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.addressbook_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addContactItem:
                Intent addNewContact = new Intent(AddressBook.this, AddEditContact.class);
                startActivity(addNewContact);
                break;
            case R.id.receiveContactItem:
                if (bluetoothAdapter.isEnabled()) {
                    Intent requestDiscoverabilityIntent = new Intent(
                            BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                    );
                    startActivityForResult(requestDiscoverabilityIntent,
                            REQUEST_DISCOVERABILITY);
                } else {
                    Toast.makeText(this, R.string.no_bluetooth, Toast.LENGTH_LONG).show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    AdapterView.OnItemClickListener viewContactListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Intent viewContact = new Intent(AddressBook.this, ViewContact.class);
            viewContact.putExtra(ROW_ID, id);
            startActivity(viewContact);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ENABLE_BLUETOOTH:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.bluetooth_enabled, Toast.LENGTH_LONG).show();
                } else {
                    userAllowedBluetooth = false;
                    Toast.makeText(this, R.string.no_bluetooth, Toast.LENGTH_LONG).show();
                }
                break;

            case REQUEST_DISCOVERABILITY:
                if (resultCode != RESULT_CANCELED) {
                    listenForContact();
                } else {
                    Toast.makeText(this, R.string.no_discoverability, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void listenForContact() {
        ReceiveContactTask task = new ReceiveContactTask();
        task.execute((Object[]) null);
    }

    // thread that listen for incoming connection request
    private class ReceiveContactTask extends AsyncTask<Object,Object,Object> {

        private BluetoothServerSocket serverSocket;
        private BluetoothSocket socket;
        @Override
        protected Object doInBackground(Object... params) {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        NAME,MY_UUID
                );

                displayToastViaHandler(AddressBook.this, handler, R.string.waiting_contact);

                BluetoothSocket socket= serverSocket.accept();

                InputStream inputStream = socket.getInputStream();

                byte[] buffer = new byte[1024]; //bytes array to hold data
                int bytesRead; //number of bytes read

                bytesRead = inputStream.read(buffer);

                if (bytesRead != -1) {
                    DatabaseConnector databaseConnector = null;

                    // convert to JSON object
                    try {
                        JSONObject contact  = new JSONObject(new String(buffer, 0, buffer.length));

                        databaseConnector = new DatabaseConnector(getBaseContext());

                        databaseConnector.open();

                        databaseConnector.insertContact(
                                contact.getString("name"),
                                contact.getString("email"),
                                contact.getString("phone"),
                                contact.getString("street"),
                                contact.getString("city")
                        );

                        //update contact list
                        new GetContactsTask().execute((Objects[]) null);
                        displayToastViaHandler(AddressBook.this, handler, R.string.contact_received);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        displayToastViaHandler(AddressBook.this, handler, R.string.contact_not_received);

                    } finally {
                        if (databaseConnector != null) databaseConnector.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (serverSocket != null) serverSocket.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
            }
            return null;
        }
    }

    public static void displayToastViaHandler(final Context context, Handler handler, final int stringID) {
        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, stringID, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }


}
