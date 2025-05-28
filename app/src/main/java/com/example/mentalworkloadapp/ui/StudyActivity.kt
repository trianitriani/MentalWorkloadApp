import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.service.EegSamplingService
import com.example.mentalworkloadapp.ui.GraphActivity
import com.example.mentalworkloadapp.ui.StartupActivity

class StudyActivity : ComponentActivity() {
    val buttonStart: Button = findViewById<Button>(R.id.btn_start)
    val buttonEnd: Button = findViewById<Button>(R.id.btn_end)
    val emojiContainer: LinearLayout = findViewById<LinearLayout>(R.id.emoji_container)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.study)

        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        // is the phase of the session of study [rest, study, vote]
        val phase = sharedPref.getString("phase", "rest")
        // indicate if the user want to training the model or not
        val voting = sharedPref.getBoolean("voting", true)
        // indicate if the user want the suggest when do a pause
        val pause = sharedPref.getBoolean("pause", false)

        // check the visibility and the enable of the component of the page
        if(phase.equals("rest")) goToRest()
        else if(phase.equals("study"))  goToStudy()
        else if(phase.equals("vote")) goToVote()

        // when the user click this button, he wants to stating a study session
        buttonStart.setOnClickListener {
            // now we have to starting the service
            if (!EegSamplingService.isRunning) {
                val intent = Intent(this, EegSamplingService::class.java)
                Log.d("EegService", "Prima della chiamata di startForegroundService.")
                ContextCompat.startForegroundService(this, intent)
                Log.d("EegService", "Dopo la chiamata di startForegroundService.")
            }
            sharedPref.edit() {
                putString("phase", "study")
            }
            goToStudy()
        }

        // when the user click this button, he wants to stopping his study session
        buttonEnd.setOnClickListener {
            // now we have to stopping the service
            if (EegSamplingService.isRunning) {
                val intent = Intent(this, EegSamplingService::class.java)
                stopService(intent)
            }
            sharedPref.edit() {
                putString("phase", "vote")
            }
            goToVote()
        }

        // when the user click one emoji, he wants to vote his mental workload now
        //
        //
        //

        // change to graph activity if the user click on the button in the footer
        val navGraph = findViewById<ImageView>(R.id.nav_graph)
        navGraph.setOnClickListener {
            val intent = Intent(this, StudyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun goToRest(){
        // enable the first button, nothing else
        buttonStart.isEnabled = true
        buttonEnd.isEnabled = false
        emojiContainer.visibility = View.GONE
    }

    private fun goToStudy(){
        // enable the second button, nothing else
        buttonStart.isEnabled = false
        buttonEnd.isEnabled = true
        emojiContainer.visibility = View.GONE
    }

    private fun goToVote() {
        // enable the emoji, nothing else
        buttonStart.isEnabled = false
        buttonEnd.isEnabled = false
        emojiContainer.visibility = View.VISIBLE
    }
}