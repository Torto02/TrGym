package com.torto.trgym.ui.home


import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels // Import for by viewModels()
import androidx.recyclerview.widget.RecyclerView
import com.torto.trgym.R
//import com.torto.trgym.adapters.TrainingCardAdapter
//import com.torto.trgym.adapters.TrainingCard
//import com.torto.trgym.ui.viewmodel.HomeViewModel

class HomeFragment : Fragment(R.layout.fragment_home) {

//    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewTrainings)

//        val trainingList = listOf(
//            TrainingCard("Giorno 1", "Full body"),
//            TrainingCard("Giorno 2", "Parte alta"),
//            TrainingCard("Giorno 3", "Gambe e core")
//        )
//
//        recyclerView.adapter = TrainingCardAdapter(trainingList)
    }
}