package id.monpres.app.ui.adminnewuser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import id.monpres.app.databinding.FragmentAdminNewUserBinding
import id.monpres.app.model.MontirPresisiUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminNewUserFragment : DialogFragment() {

    companion object {
        fun newInstance() = AdminNewUserFragment()
    }

    private val viewModel: AdminNewUserViewModel by viewModels()

    /* UI */
    private lateinit var binding: FragmentAdminNewUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAdminNewUserBinding.inflate(inflater, container, false)

        //
        val user = arguments?.getParcelable<MontirPresisiUser>("user")

        val userCreatedAtTimestamp = user?.createdAt

        // Format the timestamp to a user-readable date string
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val createdAtDate = userCreatedAtTimestamp?.let { Date(it.toLong()) }
        val formattedDate = createdAtDate?.let { sdf.format(it) } ?: "N/A"

        binding.apply {
            fragmentAdminNewUserTextViewTitle.text = user?.displayName
            fragmentAdminNewUserTextViewSubtitle.text = formattedDate
            fragmentAdminNewUserTextViewPhone.text = user?.phoneNumber
            fragmentAdminNewUserTextViewInstagramId.text = user?.instagramId
            fragmentAdminNewUserTextViewFacebookId.text = user?.facebookId
        }

        return binding.root
    }
}