package com.example.lostfoundcampus;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_IN_MAIN = "in_main";

    /** Écrans qui affichent la barre de navigation basse. */
    private static final Set<Integer> TOP_LEVEL = new HashSet<>(Arrays.asList(
            R.id.homeFragment, R.id.messagesFragment, R.id.profileFragment));

    /** Écrans à fond sombre : icônes de barre d'état claires. */
    private static final Set<Integer> DARK_SCREENS = new HashSet<>(Arrays.asList(
            R.id.loginFragment, R.id.photoPreviewFragment, R.id.cropFragment, R.id.aiAnalysisFragment));

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean inMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost);
        if (host == null) throw new IllegalStateException("NavHostFragment introuvable");
        navController = host.getNavController();

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_IN_MAIN)) {
            // Après une rotation : la pile est restaurée, on remet seulement le bon point de départ.
            inMain = true;
            navController.getGraph().setStartDestination(R.id.homeFragment);
        }

        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        binding.bottomNav.setOnItemReselectedListener(item -> {
            // Pas de rechargement quand on retouche l'onglet actif.
        });

        // Session expirée (rafraîchissement refusé) : retour à la connexion.
        AuthRepository.get(this).session().setExpiredListener(() -> {
            if (!inMain) return;
            showAuth();
            showMessage(R.string.error_session_expired);
        });

        // Démarrage : une session valide ouvre directement l'accueil.
        if (savedInstanceState == null && AuthRepository.get(this).isSignedIn()) {
            showMain();
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            binding.bottomNav.setVisibility(TOP_LEVEL.contains(id) ? View.VISIBLE : View.GONE);
            WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            boolean light = !DARK_SCREENS.contains(id);
            bars.setAppearanceLightStatusBars(light);
            bars.setAppearanceLightNavigationBars(light);
        });
    }

    @Override
    protected void onDestroy() {
        AuthRepository.get(this).session().setExpiredListener(null);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_IN_MAIN, inMain);
    }

    /** Après connexion / inscription : l'accueil devient la racine (la connexion sort de la pile). */
    public void showMain() {
        inMain = true;
        resetGraph(R.id.homeFragment);
    }

    /** Après déconnexion : retour à la connexion avec une pile vide. */
    public void showAuth() {
        inMain = false;
        resetGraph(R.id.loginFragment);
    }

    private void resetGraph(int startDestination) {
        NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        graph.setStartDestination(startDestination);
        navController.setGraph(graph, null);
    }

    /** Sélectionne un onglet de la barre basse (même comportement qu'un toucher). */
    public void selectTab(int destinationId) {
        binding.bottomNav.setSelectedItemId(destinationId);
    }

    public void showMessage(@StringRes int message) {
        showMessage(getString(message));
    }

    public void showMessage(CharSequence message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT);
        if (binding.bottomNav.getVisibility() == View.VISIBLE) snackbar.setAnchorView(binding.bottomNav);
        snackbar.show();
    }
}
