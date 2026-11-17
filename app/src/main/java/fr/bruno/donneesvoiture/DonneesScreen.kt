package fr.bruno.donneesvoiture

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Affiche, pour chaque donnée du véhicule, la valeur reçue ou la raison
 * pour laquelle elle n'est pas disponible.
 */
class DonneesScreen(carContext: CarContext) : Screen(carContext) {

    private val executor: Executor = ContextCompat.getMainExecutor(carContext)
    private val carInfo: CarInfo =
        carContext.getCarService(CarHardwareManager::class.java).carInfo

    private var modele = "en attente..."
    private var energie = "en attente..."
    private var batterie = "en attente..."
    private var autonomie = "en attente..."
    private var vitesse = "en attente..."
    private var odometre = "en attente..."

    private var actif = false

    private val ecouteurEnergie = OnCarDataAvailableListener<EnergyLevel> { d ->
        batterie = formater(d.batteryPercent) { "%.0f %%".format(Locale.FRANCE, it) }
        autonomie = formater(d.rangeRemainingMeters) { "%.0f km".format(Locale.FRANCE, it / 1000f) }
        invalidate()
    }

    private val ecouteurVitesse = OnCarDataAvailableListener<Speed> { d ->
        vitesse = formater(d.rawSpeedMetersPerSecond) { "%.0f km/h".format(Locale.FRANCE, it * 3.6f) }
        invalidate()
    }

    private val ecouteurKilometrage = OnCarDataAvailableListener<Mileage> { d ->
        odometre = formater(d.odometerMeters) { "%.0f km".format(Locale.FRANCE, it / 1000f) }
        invalidate()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                demarrer()
            }

            override fun onStop(owner: LifecycleOwner) {
                arreter()
            }
        })
    }

    private fun demarrer() {
        actif = true
        val permissions = listOf(
            "com.google.android.gms.permission.CAR_FUEL",
            "com.google.android.gms.permission.CAR_SPEED",
            "com.google.android.gms.permission.CAR_MILEAGE"
        )
        carContext.requestPermissions(permissions) { accordees, refusees ->
            if (refusees.isNotEmpty()) {
                val msg = "autorisation refusée (${refusees.size})"
                if (batterie.startsWith("en attente")) batterie = msg
                if (vitesse.startsWith("en attente")) vitesse = msg
                if (odometre.startsWith("en attente")) odometre = msg
                invalidate()
            }
            if (actif && accordees.isNotEmpty()) enregistrer()
        }
    }

    private fun enregistrer() {
        try {
            carInfo.fetchModel(executor, OnCarDataAvailableListener<Model> { m ->
                val nom = formater(m.name)
                val marque = formater(m.manufacturer)
                val annee = formater(m.year)
                modele = "$marque / $nom / $annee"
                invalidate()
            })
        } catch (e: Exception) {
            modele = "erreur : ${e.javaClass.simpleName}"
        }

        try {
            carInfo.fetchEnergyProfile(executor, OnCarDataAvailableListener<EnergyProfile> { p ->
                val prises = formater(p.evConnectorTypes) { liste ->
                    liste.joinToString(", ") { nomConnecteur(it) }
                }
                val carburants = formater(p.fuelTypes) { it.joinToString(", ") }
                energie = "prises : $prises ; carburants : $carburants"
                invalidate()
            })
        } catch (e: Exception) {
            energie = "erreur : ${e.javaClass.simpleName}"
        }

        try {
            carInfo.addEnergyLevelListener(executor, ecouteurEnergie)
        } catch (e: Exception) {
            batterie = "erreur : ${e.javaClass.simpleName}"
            autonomie = batterie
        }
        try {
            carInfo.addSpeedListener(executor, ecouteurVitesse)
        } catch (e: Exception) {
            vitesse = "erreur : ${e.javaClass.simpleName}"
        }
        try {
            carInfo.addMileageListener(executor, ecouteurKilometrage)
        } catch (e: Exception) {
            odometre = "erreur : ${e.javaClass.simpleName}"
        }
        invalidate()
    }

    private fun arreter() {
        actif = false
        try {
            carInfo.removeEnergyLevelListener(ecouteurEnergie)
            carInfo.removeSpeedListener(ecouteurVitesse)
            carInfo.removeMileageListener(ecouteurKilometrage)
        } catch (e: Exception) {
            // rien à faire
        }
    }

    private fun nomConnecteur(code: Int): String = when (code) {
        1 -> "J1772"
        2 -> "Type 2"
        3 -> "CHAdeMO"
        4 -> "CCS1"
        5 -> "CCS2"
        9 -> "GB/T"
        10 -> "GB/T DC"
        101 -> "autre"
        else -> "code $code"
    }

    private fun <T> formater(valeur: CarValue<T>, conversion: (T) -> String = { it.toString() }): String {
        val v = valeur.value
        return when (valeur.status) {
            CarValue.STATUS_SUCCESS -> if (v != null) conversion(v) else "vide"
            CarValue.STATUS_UNAVAILABLE -> "indisponible"
            CarValue.STATUS_UNKNOWN -> "inconnu"
            CarValue.STATUS_UNIMPLEMENTED -> "non transmis par la voiture"
            else -> "état ${valeur.status}"
        }
    }

    override fun onGetTemplate(): Template {
        val limite = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val lignes = listOf(
            "Véhicule" to modele,
            "Énergie" to energie,
            "Batterie" to batterie,
            "Autonomie restante" to autonomie,
            "Vitesse" to vitesse,
            "Kilométrage" to odometre
        )

        val liste = ItemList.Builder()
        lignes.take(limite).forEach { (titre, texte) ->
            liste.addItem(Row.Builder().setTitle(titre).addText(texte).build())
        }

        return ListTemplate.Builder()
            .setSingleList(liste.build())
            .setTitle("Données voiture")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
