package com.aewsn.alkhair.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.databinding.FragmentChatBinding
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsets()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.chatHeader) { v, insets ->
            val statusBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(
                v.paddingLeft,
                statusBars.top + 16, // added small extra padding for internal spacing if needed, or just statusBars.top
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
        // Force request insets to ensure the listener is called
        androidx.core.view.ViewCompat.requestApplyInsets(binding.chatHeader)
    }

    private fun setupRecyclerView() {
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // Scroll to bottom
            }
            adapter = chatAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.etMessage.text.clear()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observe Messages
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            // Scroll to bottom on new message
                            if (messages.isNotEmpty()) {
                                binding.rvChat.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                }

                // 2. Observe State (Loading/Error)
                launch {
                    viewModel.chatState.collect { state ->
                        binding.loadingLayout.isVisible = state is UiState.Loading
                        
                        if (state is UiState.Error) {
                            // Show error toast or snackbar? 
                            // Current implementation adds an error message to the list, 
                            // so maybe just generic error handling here if needed.
                        }
                    }
                }

                // 3. Observe Auto-Complete Suggestions
                launch {
                    viewModel.suggestions.collect { suggestions ->
                        android.util.Log.d("ChatFragment", "Received ${suggestions.size} suggestions")
                        val adapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            suggestions
                        )
                        binding.etMessage.setAdapter(adapter)
                        binding.etMessage.setTokenizer(SpaceTokenizer())
                        binding.etMessage.threshold = 1
                    }
                }
            }
        }
    }

    // Simple Tokenizer for Space-separated words
    class SpaceTokenizer : android.widget.MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var i = cursor
            while (i > 0 && text[i - 1] != ' ') {
                i--
            }
            while (i < cursor && text[i] == ' ') {
                i++
            }
            return i
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var i = cursor
            val len = text.length
            while (i < len) {
                if (text[i] == ' ') {
                    return i
                } else {
                    i++
                }
            }
            return len
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            var i = text.length
            while (i > 0 && text[i - 1] == ' ') {
                i--
            }
            return if (i > 0 && text[i - 1] == ' ') {
                text
            } else {
                if (text is android.text.Spanned) {
                    val sp = android.text.SpannableString("$text ")
                    android.text.TextUtils.copySpansFrom(
                        text, 0, text.length,
                        Any::class.java, sp, 0
                    )
                    sp
                } else {
                    "$text "
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ChatFragment()
    }
}
