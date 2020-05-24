package com.shevaalex.android.rickmortydatabase.ui.episode;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.shevaalex.android.rickmortydatabase.databinding.ItemEpisodeBinding;
import com.shevaalex.android.rickmortydatabase.source.database.Episode;

public class EpisodeAdapter extends PagedListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder> {
    private OnEpisodeClickListener clickListener;

    EpisodeAdapter (OnEpisodeClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Episode> DIFF_CALLBACK = new DiffUtil.ItemCallback<Episode>() {
        @Override
        public boolean areItemsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
            return oldItem.getId() == newItem.getId();
        }
        @Override
        public boolean areContentsTheSame(@NonNull Episode oldItem, @NonNull Episode newItem) {
            return newItem.equals(oldItem);
        }
    };

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //instantiate view binding class and pass it to ViewHolder
        ItemEpisodeBinding binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode currentEpisode = getItem(position);
        if (currentEpisode != null) {
            holder.binding.episodeNameValue.setText(currentEpisode.getName());
            holder.binding.episodeAirDateValue.setText(currentEpisode.getAirDate());
            holder.binding.episodeCodeValue.setText(currentEpisode.getCode());
        }
    }

    class EpisodeViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ItemEpisodeBinding binding;

        EpisodeViewHolder (ItemEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (getAbsoluteAdapterPosition() != RecyclerView.NO_POSITION) {
                clickListener.onEpisodeClick(getAbsoluteAdapterPosition(), v);
            }
        }
    }

    public interface OnEpisodeClickListener {
        void onEpisodeClick (int position, View v);
    }
}
