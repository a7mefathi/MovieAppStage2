package com.example.android.movieapp.activity;

import android.app.ProgressDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.android.movieapp.BuildConfig;
import com.example.android.movieapp.R;
import com.example.android.movieapp.adapter.MovieAdapter;
import com.example.android.movieapp.api.Client;
import com.example.android.movieapp.api.MovieService;
import com.example.android.movieapp.data.Favorite;
import com.example.android.movieapp.model.Movie;
import com.example.android.movieapp.model.MovieResponse;
import com.example.android.movieapp.viewmodels.MainViewModel;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.support.v7.preference.PreferenceManager;


public class DiscoveryActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        MovieAdapter.ListItemClickListener {

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.discovery_main)
    SwipeRefreshLayout refreshLayout;

    private MovieAdapter adapter;
    private ArrayList<Movie> movieData = new ArrayList<>();

    private final String DATA_STATE = "saved_state";
    private final String LIST_STATE = "recycler_state";
    private Parcelable savedRecyclerViewState;
    GridLayoutManager mGridLayoutManager;

    MainViewModel viewModel;
    public static final String LOG_TAG = MovieAdapter.class.getName();

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_descovery_screen);
        ButterKnife.bind(this);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        setupRecyclerView();

        setupSharedPreferences(sharedPreferences);

        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshLayout.setRefreshing(false);
            }
        });

    }

    private void setupRecyclerView() {

        mGridLayoutManager = new GridLayoutManager(this, calculateNoOfColumns(this));
        adapter = new MovieAdapter(movieData, this);
        recyclerView.setLayoutManager(mGridLayoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }


    public static int calculateNoOfColumns(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        int scalingFactor = 200;
        int noOfColumns = (int) (dpWidth / scalingFactor);
        if (noOfColumns < 2)
            noOfColumns = 2;
        return noOfColumns;
    }


    private void loadMovies(String sortCriteria) {

        try {

            MovieService apiService =
                    Client.getClient().create(MovieService.class);

            Call<MovieResponse> call = apiService.getMovies(sortCriteria, BuildConfig.THE_MOVIE_DB_API_TOKEN);
            call.enqueue(new Callback<MovieResponse>() {

                @Override
                public void onResponse(@NonNull Call<MovieResponse> call, @NonNull Response<MovieResponse> response) {
                    if (response.body() != null) {
                        movieData.clear();
                        List<Movie> movieList = response.body().getResults();

                        adapter.setMovieList(movieList);

                        movieData = (ArrayList<Movie>) movieList;

                    }
                }

                @Override
                public void onFailure(@NonNull Call<MovieResponse> call, @NonNull Throwable t) {

                    Log.d("Error", t.getMessage());
                    Toast.makeText(DiscoveryActivity.this, "Error fetching data!", Toast.LENGTH_SHORT).show();

                }

            });

        } catch (Exception e) {
            Log.d("Error", e.getMessage());
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {


        setupSharedPreferences(sharedPreferences);
        Log.d(LOG_TAG, "Preferences updated");

        if (refreshLayout.isRefreshing()) {
            refreshLayout.setRefreshing(false);
        }
    }

    private void displayFavorites() {
        movieData.clear();
        viewModel.getFavorite().observe(this, new Observer<List<Favorite>>() {
            @Override
            public void onChanged(@Nullable List<Favorite> favorites) {

                if (favorites != null) {
                    for (Favorite entry : favorites) {
                        Movie movie = new Movie();
                        movie.setId(entry.getMovieid());
                        movie.setOverview(entry.getOverview());
                        movie.setOriginalTitle(entry.getTitle());
                        movie.setPosterPath(entry.getPoster());
                        movie.setVoteAverage(entry.getRating());
                        movie.setBackdropPath(entry.getBackdrop());
                        movie.setReleaseDate(entry.getReleaseDate());
                        movieData.add(movie);
                    }
                    adapter.setMovieList(movieData);
                }


            }
        });


    }

    private void setupSharedPreferences(SharedPreferences sharedPreferences) {

        String prefValue = sharedPreferences.getString(this.getString(R.string.pref_sort_order_key),
                this.getString(R.string.pref_sort_order_popular));
        if (prefValue.equals(this.getString(R.string.pref_sort_order_popular))) {
            loadMovies(this.getString(R.string.pref_popular_value));

        } else if (prefValue.equals(this.getString(R.string.pref_sort_order_top_rated))) {
            loadMovies(this.getString(R.string.pref_top_rated_value));
        } else {

            displayFavorites();
        }

    }

    protected void onSaveInstanceState(Bundle state) {
        // Save list state
        savedRecyclerViewState = mGridLayoutManager.onSaveInstanceState();
        state.putParcelable(LIST_STATE, savedRecyclerViewState);
        Log.d(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(state);
    }

    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);


        if (state != null) {
            savedRecyclerViewState = state.getParcelable(LIST_STATE);
            Log.d(LOG_TAG, "onRestoreInstanceState");
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (savedRecyclerViewState != null) {
            mGridLayoutManager.onRestoreInstanceState(savedRecyclerViewState);
            Log.d(LOG_TAG, "onResume");
        }
    }

    @Override
    public void onListItemClick(int clickedItemIndex) {

        Movie clickDataItem = movieData.get(clickedItemIndex);
        Intent intent = new Intent(DiscoveryActivity.this, DetailActivity.class);
        intent.putExtra("movieitem", clickDataItem);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        Toast.makeText(this, "you clicked " + clickDataItem.getOriginalTitle(), Toast.LENGTH_SHORT).show();

    }
}
