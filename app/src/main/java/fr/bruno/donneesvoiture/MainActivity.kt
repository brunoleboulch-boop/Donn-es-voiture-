package fr.bruno.donneesvoiture

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texte = TextView(this).apply {
            setPadding(48, 64, 48, 48)
            textSize = 17f
            text = "Données voiture\n\n" +
                "Cette application s'affiche sur l'écran de la voiture, pas sur le téléphone.\n\n" +
                "1. Dans Android Auto (réglages du téléphone), touchez dix fois « Version », " +
                "puis menu à trois points > Paramètres pour les développeurs > cochez « Sources inconnues ».\n" +
                "2. Branchez ou connectez le téléphone à la voiture.\n" +
                "3. Sur l'écran de la voiture, ouvrez la liste des applications et lancez « Données voiture ».\n" +
                "4. Acceptez les autorisations demandées sur le téléphone.\n\n" +
                "L'écran indique pour chaque donnée (batterie, autonomie, vitesse, kilométrage) " +
                "si la voiture la transmet ou non."
        }
        setContentView(ScrollView(this).apply { addView(texte) })
    }
}
