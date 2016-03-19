package com.example.homepc.addressbookdeitel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.support.annotation.Nullable;

import android.widget.TextView;

/**
 * Created by HomePC on 18/3/2016.
 */
public class ViewContact extends Activity {
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

        nameTextView = (TextView) findViewById(R.id.nameTextView);
        phoneTextView = (TextView) findViewById(R.id.phoneTextView);
        emailTextView = (TextView) findViewById(R.id.emailTextView);
        streetTextView = (TextView) findViewById(R.id.streetTextView);
        cityTextView = (TextView) findViewById(R.id.cityTextView);

        Bundle extras = getIntent().getExtras();
        rowID = extras.getLong("row_id");
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


}
