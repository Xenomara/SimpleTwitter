package com.codepath.apps.restclienttemplate;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.codepath.apps.restclienttemplate.models.Tweet;
import com.codepath.apps.restclienttemplate.models.TweetDao;
import com.codepath.apps.restclienttemplate.models.TweetWithUser;
import com.codepath.apps.restclienttemplate.models.User;
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Headers;

public class TimelineActivity extends AppCompatActivity {

    public static final String TAG = "TimelineActivity";
    private final int REQUEST_CODE = 20;

    TweetDao tweetDao;
    TwitterClient client;
    RecyclerView rvTweets;
    List<Tweet> tweets;
    TweetsAdapter adapter;
    SwipeRefreshLayout swipeContainer;
    EndlessRecyclerViewScrollListener scrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);

        client = TwitterApplication.getRestClient(this);
        tweetDao = ((TwitterApplication) getApplicationContext()).getMyDatabase().tweetDao();

        swipeContainer = findViewById(R.id.swipeContainer);
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(TAG, "Fetching new data!");
                populateHomeTimeLine();
            }
        });

        // Find the recycler view
        rvTweets = findViewById(R.id.rvTweets);
        // Initialize list of tweets and adapter
        tweets = new ArrayList<>();
        adapter = new TweetsAdapter(this, tweets);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // Recycler view setup: layout manager & adapter
        rvTweets.setLayoutManager(layoutManager);
        rvTweets.setAdapter(adapter);

        scrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                Log.i(TAG, "OnLoadMore: " + page);
                loadMoreData();
            }
        };
        // Add scroll listener to recycler view
        rvTweets.addOnScrollListener(scrollListener);

        // Query for existing tweets in the DB
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Showing data from database");
                List<TweetWithUser> tweetWithUsers = tweetDao.recentItems();
                List<Tweet> tweetsFromDB = TweetWithUser.getTweetList(tweetWithUsers);
                adapter.clear();
                adapter.addAll(tweetsFromDB);
            }
        });

        populateHomeTimeLine();
    }

    private void loadMoreData() {
        // 1. Send an API request to retrieve appropriate paginated data
        client.getNextPageOfTweets(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                Log.i(TAG, "OnSuccess for loadMoreData: " + json.toString());
                // 2. Deserialize and construct new model objects from the API response
                JSONArray jsonArray = json.jsonArray;
                try {
                    List<Tweet> tweets = Tweet.fromJsonArray(jsonArray);
                    // 3. Append the new data objects to the existing set of items inside the array of items
                    // 4. Notify the adapter of the new items made with `notifyItemRangeInserted()
                    adapter.addAll(tweets);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.e(TAG, "OnFailure for loadMoreData: ", throwable);
            }
        }, tweets.get(tweets.size()-1).id);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.compose) {
            // compose icon has been tapped
            Toast.makeText(this, "Compose!", Toast.LENGTH_SHORT).show();
            // navigate to compose activity
            Intent intent = new Intent(this, ComposeActivity.class);
            startActivityForResult(intent, REQUEST_CODE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            // Get data from intent
            Tweet tweet = Parcels.unwrap(data.getParcelableExtra("tweet"));
            // Update recycler view with new tweet
            // Modify data source of tweets
            tweets.add(0, tweet);
            // Update the adapter
            adapter.notifyItemInserted(0);
            rvTweets.smoothScrollToPosition(0);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void populateHomeTimeLine() {
        client.getHomeTimeline(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                Log.i(TAG, "OnSuccess!" + json.toString());
                JSONArray jsonArray = json.jsonArray;
                try {
                    final List<Tweet> tweetsFromNetwork = Tweet.fromJsonArray(jsonArray);
                    adapter.clear();
                    adapter.addAll(tweetsFromNetwork);
                    // lets app know that refresh has finished, stop showing refresh symbol
                    swipeContainer.setRefreshing(false);
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Saving data into database");
                            List<User> usersFromNetwork = User.fromJsonTweetArray(tweetsFromNetwork);
                            // Insert users first
                            tweetDao.insertModel(usersFromNetwork.toArray(new User[0]));
                            // Insert tweets
                            tweetDao.insertModel(tweetsFromNetwork.toArray(new Tweet[0]));
                        }
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "Json exception: ", e);
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.e(TAG, "OnFailure!: " + response, throwable);
            }
        });
    }
}