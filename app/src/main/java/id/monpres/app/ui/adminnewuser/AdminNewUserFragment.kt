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
        private const val ARG_USER = "user" // Use a constant for the key
        val TAG = AdminNewUserFragment::class.simpleName
        fun newInstance() = AdminNewUserFragment()

        /**
         * Creates a new instance of AdminNewUserFragment with the provided user data.
         * @param user The User object to be passed to the fragment.
         * @return A new instance of AdminNewUserFragment.
         */
        fun newInstance(user: MontirPresisiUser): AdminNewUserFragment {
            val fragment = AdminNewUserFragment()
            val args = Bundle().apply {
                putParcelable(ARG_USER, user)
            }
            fragment.arguments = args
            return fragment
        }
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