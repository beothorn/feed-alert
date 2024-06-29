import java.awt.*
import javax.swing.*

fun showDeleteFeed(
    dialogIcon: Image,
    entries: List<RssEntry>,
    onDeleteFeed: (feedUrl: String) -> Unit
) {
    // COPY ENTRIES LIST TO ONE EDITABLE LIST
    val editableEntries = entries.toMutableList()

    fun refreshDialog(dialog: JDialog) {
        dialog.contentPane.removeAll()
        val gbc = GridBagConstraints()
        gbc.insets = Insets(10, 10, 10, 10)
        gbc.fill = GridBagConstraints.HORIZONTAL

        editableEntries.forEachIndexed { index, entry ->
            gbc.gridy = index

            gbc.gridx = 0
            gbc.weightx = 0.0
            dialog.add(JLabel("Feed URL:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            val feedUrlField = JTextField(entry.url)
            feedUrlField.isEditable = false
            dialog.add(feedUrlField, gbc)

            gbc.gridx = 2
            gbc.weightx = 0.0
            dialog.add(JLabel("Polling time (seconds):"), gbc)

            gbc.gridx = 3
            gbc.weightx = 0.0
            val pollingTimeField = JTextField(entry.pollingIntervalInSeconds.toString())
            pollingTimeField.preferredSize = Dimension(50, pollingTimeField.preferredSize.height)
            feedUrlField.isEditable = false
            dialog.add(pollingTimeField, gbc)

            gbc.gridx = 4
            gbc.weightx = 0.0
            val disableAfterNotifyCheckbox = JCheckBox("Delete after notify", entry.deleteAfterNotification)
            disableAfterNotifyCheckbox.isEnabled = false
            dialog.add(disableAfterNotifyCheckbox, gbc)

            gbc.gridx = 5
            gbc.weightx = 0.0
            gbc.anchor = GridBagConstraints.CENTER
            val deleteButton = JButton("DELETE")
            deleteButton.addActionListener {
                SwingUtilities.invokeLater {
                    editableEntries.remove(entry)
                    onDeleteFeed(entry.url)
                    if (editableEntries.size == 0) {
                        dialog.dispose()
                    } else {
                        refreshDialog(dialog)
                    }
                }
            }
            dialog.add(deleteButton, gbc)
        }

        gbc.gridy = editableEntries.size
        gbc.gridx = 0
        gbc.gridwidth = GridBagConstraints.REMAINDER
        gbc.anchor = GridBagConstraints.CENTER

        val closeButton = JButton("Close")
        closeButton.addActionListener {
            dialog.dispose()
        }
        dialog.add(closeButton, gbc)

        dialog.revalidate()
        dialog.repaint()
        dialog.pack()
        dialog.setLocationRelativeTo(null)
    }

    val dialog = JDialog(null as Frame?, "Delete Feeds", true)
    dialog.setIconImage(dialogIcon)
    dialog.layout = GridBagLayout()
    refreshDialog(dialog)
    dialog.isVisible = true
}