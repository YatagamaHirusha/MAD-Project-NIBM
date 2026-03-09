package com.mad.cw.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.imageview.ShapeableImageView;
import com.mad.cw.R;
import com.mad.cw.models.Message;
import java.util.List;

public class NewMatchAdapter extends RecyclerView.Adapter<NewMatchAdapter.ViewHolder> {

    private List<Message> matches;

    public NewMatchAdapter(List<Message> matches) {
        this.matches = matches;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_new_match, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message match = matches.get(position);
        holder.tvName.setText(match.getName());
        holder.ivAvatar.setImageResource(match.getAvatarResId());
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivAvatar;
        TextView tvName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_new_match_avatar);
            tvName = itemView.findViewById(R.id.tv_new_match_name);
        }
    }
}
