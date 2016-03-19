package com.example.homepc.addressbookdeitel;


import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class AddressBook extends ListActivity {

    public static final String ROW_ID = "row_id";
    private ListView contactListView;
    private CursorAdapter contactAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contactListView = getListView();
        contactListView.setOnItemClickListener(viewContactListener);

        String[] from = new String[] {"name"};
        int[] to = new int[] {R.id.contactTextView};
        contactAdapter = new SimpleCursorAdapter(
                AddressBook.this, R.layout.contact_list_item, null, from, to, 1);
        setListAdapter(contactAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

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
        Intent addNewContact = new Intent(AddressBook.this, AddEditContact.class);
        startActivity(addNewContact);
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


}
