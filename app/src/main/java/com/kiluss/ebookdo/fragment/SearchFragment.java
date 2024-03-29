package com.kiluss.ebookdo.fragment;

import static android.content.ContentValues.TAG;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.kiluss.ebookdo.OnTextClickListener;
import com.kiluss.ebookdo.R;
import com.kiluss.ebookdo.adapter.BookPreviewAdapter;
import com.kiluss.ebookdo.adapter.SearchHistoryAdapter;
import com.kiluss.ebookdo.custom.CustomLinearLayoutManager;
import com.kiluss.ebookdo.model.BookDetailModel;
import com.kiluss.ebookdo.process.BookData;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;


public class SearchFragment extends Fragment {
    public static String MY_URL = "https://www.gutenberg.org/";
    public static String SEARCH_URL = "https://www.gutenberg.org/";
    private RecyclerView recyclerViewResult;
    private RecyclerView recyclerViewHistory;
    private BookPreviewAdapter bookPreviewAdapter;
    private SearchHistoryAdapter searchHistoryAdapter;
    private ArrayList<BookDetailModel> listBook;
    private ArrayList<String> listHistory;
    private int scrollPosition;
    private FloatingActionButton fabToTopList;
    private CustomLinearLayoutManager linearLayoutManager;
    private CustomLinearLayoutManager linearLayoutManagerHis;
    private EditText searchText;
    private int startIndex;
    private boolean endOfResult = false;
    private ShimmerFrameLayout shimmerFrameLayout;
    private View v;
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDatabaseReference;
    private DateTimeFormatter dtf;
    private String android_id;

    private boolean isLoading = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        //create unique id for each device
        android_id = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        //create database
        mDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mDatabase.getReference().child(android_id);
        //addPostEventListener(mDatabaseReference);
        Log.i("test","search fragment attach");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.fragment_search, container, false);

        recyclerViewResult = v.findViewById(R.id.rcv_search_fragment);
        recyclerViewHistory = v.findViewById(R.id.rcv_history_fragment);
        fabToTopList = v.findViewById(R.id.fab_to_top_search);
        searchText = (EditText)v.findViewById(R.id.search_view);
        shimmerFrameLayout = v.findViewById(R.id.shimmer_view_search_container);

        shimmerFrameLayout.setVisibility(GONE);

        Log.i("test","search fragment oncreate");

        linearLayoutManager = new CustomLinearLayoutManager(this.requireContext());
        linearLayoutManagerHis = new CustomLinearLayoutManager(this.requireContext());
        recyclerViewResult.setLayoutManager(linearLayoutManager);
        listBook = new ArrayList<>();
        recyclerViewHistory.setLayoutManager(linearLayoutManagerHis);
        dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Log.d(TAG, "Time is: " + dtf.format(LocalDateTime.now()));
        //RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        //recyclerView.addItemDecoration(itemDecoration);

        //tao query and xu li vs database
        Query query = mDatabaseReference.limitToLast(5);
        query.addValueEventListener(new ValueEventListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Get Post object and use the values to update the UI
                Map<String, String> map = (Map<String, String>) snapshot.getValue();
                Log.d(TAG, "map is: " + map);
                if (map != null) {
                    List<String> keyList = new ArrayList<>(map.keySet());
                    Collections.sort(keyList);
                    Collections.reverse(keyList);
                    List<String> strings = new ArrayList<>();
                    for (String key: keyList) {
                        strings.add(map.get(key));
                    }
                    Log.d(TAG, "Key is: " + keyList.toString());
                    listHistory = (ArrayList) strings;
                    Log.d(TAG, "string is: " + listHistory.toString());
                    searchHistoryAdapter = new SearchHistoryAdapter(listHistory, new OnTextClickListener() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onTextClick(String data) {
                            recyclerViewResult.setVisibility(View.INVISIBLE);
                            searchText.setText(data);
                            hideKeyboard(requireContext(),v);
                            mDatabaseReference.child(dtf.format(LocalDateTime.now())).setValue(data);
                            endOfResult = false;
                            listBook.clear();
                            bookPreviewAdapter = null;
                            startIndex = 1;
                            MY_URL =  processSearchInput(searchText.getText().toString()) + startIndex;
                            SEARCH_URL = processSearchInput(searchText.getText().toString());
                            Log.i("result", MY_URL);
                            new SearchFragment.DownloadTask().execute(MY_URL);
                            recyclerViewHistory.setVisibility(View.INVISIBLE);
                            shimmerFrameLayout.setVisibility(View.VISIBLE);
                        }
                        @Override
                        public void onDeleteClick(int pos){
                            mDatabaseReference.child(keyList.get(pos)).removeValue();
                        }
                    });
                    recyclerViewHistory.setAdapter(searchHistoryAdapter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "loadPost:onCancelled", error.toException());
            }
        });

        initScrollListener();
        fabToTopList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerViewResult.smoothScrollToPosition(0);
            }
        });

        searchText.requestFocus();
        showKeyboard(requireContext());

        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchText.onEditorAction(EditorInfo.IME_ACTION_DONE);
                    endOfResult = false;
                    listBook.clear();
                    bookPreviewAdapter = null;
                    startIndex = 1;
                    MY_URL =  processSearchInput(searchText.getText().toString()) + startIndex;
                    SEARCH_URL = processSearchInput(searchText.getText().toString());
                    Log.i("result", MY_URL);
                    recyclerViewResult.setVisibility(View.INVISIBLE);
                    new SearchFragment.DownloadTask().execute(MY_URL);
                    recyclerViewHistory.setVisibility(View.INVISIBLE);
                    shimmerFrameLayout.setVisibility(View.VISIBLE);
                    mDatabaseReference.child(dtf.format(LocalDateTime.now())).setValue(searchText.getText().toString());
                    return true;
                }
                return false;
            }
        });

        searchText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerViewResult.setVisibility(View.INVISIBLE);
                shimmerFrameLayout.setVisibility(View.INVISIBLE);
                recyclerViewHistory.setVisibility(View.VISIBLE);
            }
        });

        // Inflate the layout for this fragment
        return v;
    }

    public static void showKeyboard(Context context) {
        ((InputMethodManager) (context).getSystemService(Context.INPUT_METHOD_SERVICE))
                .toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    public static void hideKeyboard(Context context, View view) {
        ((InputMethodManager) (context).getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(view.getApplicationWindowToken(),0 );
    }

    public String processSearchInput(String input) {
        String result = "https://www.gutenberg.org/ebooks/search/?query="+ input.replace(" ","+") + "&start_index=";
        return result;
    }


    //Download HTML bằng AsynTask
    private class DownloadTask extends AsyncTask<String, Void, ArrayList<BookDetailModel>> {

        private static final String TAG = "DownloadTask";

        @Override
        protected ArrayList<BookDetailModel> doInBackground(String... strings) {
            listBook.addAll(executeMainContent(strings[0]));
            return listBook;
        }

        // sau khi get duoc all data vao ham nay de set giao dien
        @Override
        protected void onPostExecute(ArrayList<BookDetailModel> books) {
            super.onPostExecute(books);
            //Setup data recyclerView
            if(getActivity() != null) {
                if (bookPreviewAdapter == null) {
                    bookPreviewAdapter = new BookPreviewAdapter(getActivity(),books);
                    recyclerViewResult.setAdapter(bookPreviewAdapter);
                    // stop man hinh loading
                    shimmerFrameLayout = v.findViewById(R.id.shimmer_view_search_container);
                    shimmerFrameLayout.setVisibility(GONE);
                    // show list book len
                    if(recyclerViewHistory.getVisibility() == View.INVISIBLE) {
                        recyclerViewResult.setVisibility(View.VISIBLE);
                    }
                } else if (listBook.size() > 0) {
                    if (listBook.get(scrollPosition - 1) == null) {
                        listBook.remove(scrollPosition - 1);
                    }
                    bookPreviewAdapter.notifyItemRemoved(scrollPosition-1);
                    bookPreviewAdapter.notifyDataSetChanged();
                    isLoading = false;
                }
            }
            if(recyclerViewHistory.getVisibility() == View.INVISIBLE) {
                recyclerViewResult.setVisibility(View.VISIBLE);
            }
        }
    }

    // ham jsoup lay data main content
    private ArrayList<BookDetailModel> executeMainContent(String url) {
        Document document = null;
        ArrayList<BookDetailModel> mList = new ArrayList<>();
        try {
            document = (Document) Jsoup.connect(url).timeout(30000).get();
            if (document != null) {
                //Lấy  html có thẻ như sau: div#latest-news > div.row > div.col-md-6 hoặc chỉ cần dùng  div.col-md-6
                Elements sub = document.select(
                        "div.page_content " +
                                "> div.body " +
                                "> div " +
                                "> ul.results " +
                                "> li.booklink");
                if (sub != null) {
                    int i =0;
                    for (Element element : sub) {
                        BookDetailModel book;
                        String bookUrl = "https://www.gutenberg.org" + element.getElementsByClass("link").attr("href");
                        BookData bookData = new BookData();
                        book = bookData.getItemBook(bookUrl);

                        //Add to list
                        mList.add(book);
                        i++;
                        if (i==5) {
                            startIndex += i;
                            MY_URL = SEARCH_URL + startIndex;
                            break;
                        }
                    }
                    if (i<5) {
                        endOfResult = true;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(),"End of results",Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else {
                    endOfResult = true;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(),"End of results",Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mList;
    }

    //ham xu ly viec cuon recyclerViewResult
    private void initScrollListener() {
        recyclerViewResult.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                // hien thi nut ve dau trang
                if(linearLayoutManager.findLastVisibleItemPosition() > 10)
                    fabToTopList.setVisibility(View.VISIBLE);
                else
                    fabToTopList.setVisibility(View.INVISIBLE);
                if (bookPreviewAdapter != null) {
                    if (!isLoading && !endOfResult) { // not in loading state
                        if (linearLayoutManager != null && linearLayoutManager.findLastCompletelyVisibleItemPosition() == listBook.size() - 1) {
                            isLoading = true;
                            Log.i("size", Integer.toString(listBook.size()));
                            //bottom of list!
                            loadMore();
                        }
                    }
                }
            }
        });
    }

    private void loadMore() {
        listBook.add(null); // kich hoat loading view ben adapter
        bookPreviewAdapter.notifyItemInserted(listBook.size() - 1);

        scrollPosition = listBook.size();//lay vi tri loading view
        new SearchFragment.DownloadTask().execute(MY_URL);
    }
}