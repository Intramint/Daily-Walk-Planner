import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.marcin.dailywalkplanner.R
import com.marcin.dailywalkplanner.Walk

class WalkAdapter(
    private var walks: List<Walk> = emptyList(),
    private val onItemClick: (Walk) -> Unit
) : RecyclerView.Adapter<WalkAdapter.WalkViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_walk, parent, false)
        return WalkViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalkViewHolder, position: Int) {
        val walk = walks[position]
        holder.startText.text = "Start: ${walk.startAddress}"
        holder.destinationText.text = "Destination: ${walk.destinationAddress}"
        holder.durationText.text = "Duration: ${walk.duration}"
        holder.distanceText.text = "Distance: ${walk.distance}"

        holder.itemView.setOnClickListener {
            onItemClick(walk)
        }
    }

    override fun getItemCount() = walks.size

    fun updateWalks(newWalks: List<Walk>) {
        walks = newWalks
        notifyDataSetChanged()
    }

    class WalkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val startText: TextView = itemView.findViewById(R.id.startText)
        val destinationText: TextView = itemView.findViewById(R.id.destinationText)
        val durationText: TextView = itemView.findViewById(R.id.durationText)
        val distanceText: TextView = itemView.findViewById(R.id.distanceText)
    }
}
