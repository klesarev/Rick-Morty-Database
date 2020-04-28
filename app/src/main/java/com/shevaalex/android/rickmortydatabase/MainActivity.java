package com.shevaalex.android.rickmortydatabase;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.shevaalex.android.rickmortydatabase.databinding.ActivityMainBinding;
import com.shevaalex.android.rickmortydatabase.ui.BottomNavViewModel;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;
    private boolean backPressedOnce;
    private BottomNavViewModel botNavViewModel;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        setupViews();
    }

    private void setupViews() {
        // Finding the navigation controller
        navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        // Setting the nav controller with bottom navigation
        NavigationUI.setupWithNavController(binding.bottomPanel, navController);
        //Set the action bar to show appropriate titles, set top level destinations
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.charactersListFragment,
                R.id.locationsListFragment, R.id.episodesListFragment).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        //setup the ViewModel for lifecycle aware observing bottomNav state
        botNavViewModel = new ViewModelProvider(this).get(BottomNavViewModel.class);
        botNavViewModel.getBottomNavVisibility().observe(this, integer -> binding.bottomPanel.setVisibility(integer));
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.characterDetailFragment2) {
                botNavViewModel.hideBottomNav();
            } else {
                botNavViewModel.showBottomNav();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) ||
                super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if(navController == null) {
            navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        }
        // Check if the current destination is actually the start destination (Home screen)
        if (navController.getCurrentDestination() != null
                && navController.getGraph().getStartDestination() == navController.getCurrentDestination().getId()){
            if(backPressedOnce){
                super.onBackPressed();
                return;
            }
            backPressedOnce = true;
            Toast.makeText(this, "Tap BACK again to exit", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(() -> backPressedOnce = false, 2000);
        } else { super.onBackPressed(); }
    }

}