package it.polito.mad.madmax

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.metrics.AddTrace
import com.squareup.picasso.Picasso
import it.polito.mad.madmax.data.viewmodel.UserViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_nav_header.view.*


class MainActivity : AppCompatActivity() {

    // User VM
    private val userVM: UserViewModel by viewModels()

    // Firebase auth
    private val auth: FirebaseAuth = Firebase.auth
    private var loggingIn: Boolean = false
    private var isListeningToUserVM: Boolean = false

    // Navigation
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration

    @RequiresApi(Build.VERSION_CODES.N)
    @AddTrace(name = "MainActivity-oncreate", enabled = true)
    override fun onCreate(savedInstanceState: Bundle?) {
        Trace.beginSection("main_onCreate")
        super.onCreate(savedInstanceState)
        Trace.beginSection("Inflate activity main")
        setContentView(R.layout.activity_main)
        Trace.endSection()
        // Action bar
        app_bar_layout.setBackgroundResource(0)
        setSupportActionBar(main_toolbar)

        // Setup Navigation
        navController = findNavController(R.id.main_nav_host_fragment)
        /*main_nav.apply {
            setupWithNavController(navController)
        }*/
        appBarConfig = AppBarConfiguration(
            setOf(
                R.id.nav_item_list_fragment,
                R.id.nav_on_sale_list_fragment,
                R.id.nav_items_of_interest_fragment,
                R.id.nav_bought_items_list_fragment,
                R.id.nav_show_profile_fragment
            ), main_drawer_layout
        )
        setupActionBarWithNavController(navController, appBarConfig)



        main_drawer_layout.addDrawerListener(object : DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerOpened(drawerView: View) {

                val stubExists = nav_stub != null
                if (stubExists) {
                    nav_stub.inflate()
                }
                if (!isListeningToUserVM){
                    setUserVMListener()
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                // Whatever you want
            }

            override fun onDrawerStateChanged(newState: Int) {

            }
        })

        userVM.getCurrentUserData().observe(this, Observer { user ->
            val stubExists = nav_stub != null
            if(!stubExists) {
                updateNavigationMenu(user)
            }

        })

        // If the user has no id it means that we just created the VM and we need to login
        if (userVM.getCurrentUserId() == "" && !loggingIn) {
            loggingIn = true
            login()
        }
        Trace.endSection()
    }

    fun setUserVMListener(){
        isListeningToUserVM = true
        userVM.getCurrentUserData().observe(this, Observer { user ->
            updateNavigationMenu(user)
        })

    }


    override fun onSupportNavigateUp(): Boolean {

        return navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        // Close drawer instead of exiting
        if (main_drawer_layout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RC_GOOGLE_SIGN_IN -> {
                try {
                    // Sign in
                    val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(
                        ApiException::class.java
                    )!!
                    auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(
                            account.idToken,
                            null
                        )
                    ).addOnCompleteListener(this) { login() }
                } catch (e: ApiException) {
                    e.printStackTrace()
                    login()
                }
            }
        }
    }

    private fun login() {
        // If the user is already logged in, just start listening on its data
        auth.currentUser?.also {
            loggingIn = false
            userVM.listenCurrentUser(it)
            FirebaseMessaging.getInstance().subscribeToTopic("/topics/${it.uid}")
//            displayMessage(this, "Welcome back ${it.displayName}")
        } ?: run {
            // Intent dialog for logging in with google
            val googleSignInClient = GoogleSignIn.getClient(
                this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
            )
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }
    }

    private fun closeDrawer() {
        main_drawer_layout.closeDrawer(GravityCompat.START)
    }

    fun removeTopLevelProfile() {
        setupActionBarWithNavController(navController, appBarConfig.apply {
            topLevelDestinations.remove(R.id.nav_show_profile_fragment)
        })
    }

    fun addTopLevelProfile() {
        setupActionBarWithNavController(navController, appBarConfig.apply {
            topLevelDestinations.add(R.id.nav_show_profile_fragment)
        })
    }

    private fun updateNavigationMenu(user: it.polito.mad.madmax.data.model.User){

        findViewById<NavigationView>(R.id.main_nav).getHeaderView(0).apply {
            main_nav_header_nickname.text = user.name
            main_nav_header_email.text = user.email
            main_nav_header_photo.post {
                main_nav_header_photo.apply {
                    if (user.photo != "") {
                        translationY = 0F
                        Thread {
                            val img = Picasso.get().load(Uri.parse(user.photo)).get()
                            this.post {
                                setImageBitmap(img)
                            }
                        }.start()
                    } else {
                        translationY = measuredHeight / 6F
                        setImageDrawable(getDrawable(R.drawable.ic_profile))
                    }
                    setOnClickListener {
                        closeDrawer()
                        navController.navigate(R.id.action_global_show_profile)
                    }
                }
            }
        }
    }

    // Companion
    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

}

