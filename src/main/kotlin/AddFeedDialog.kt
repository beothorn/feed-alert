import java.awt.*
import javax.swing.*

fun showAddFeed(
    dialogIcon: Image,
    onSaveFeed: (feedUrl: String, pollingTime: Int, disableAfterNotify: Boolean) -> Unit
) {
    val dialog = JDialog(
        null as Frame?,
        "Add Feed",
        true
    )
    dialog.setIconImage(dialogIcon)
    dialog.layout = GridBagLayout()
    val gbc = GridBagConstraints()
    gbc.insets = Insets(10, 10, 10, 10)
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.gridy = 0

    gbc.gridx = 0
    gbc.weightx = 0.0
    dialog.add(JLabel("Feed URL:"), gbc)

    gbc.gridx = 1
    gbc.weightx = 1.0
    val feedUrlField = JTextField(20)
    dialog.add(feedUrlField, gbc)

    gbc.gridx = 2
    gbc.weightx = 0.0
    dialog.add(JLabel("Polling time (seconds):"), gbc)

    gbc.gridx = 3
    gbc.weightx = 0.0
    val feedPollingTimeModel: SpinnerModel = SpinnerNumberModel(
        300,
        1,
        Int.MAX_VALUE,
        1
    )
    val pollingTimeField = JSpinner(feedPollingTimeModel)
    pollingTimeField.preferredSize = Dimension(50, pollingTimeField.preferredSize.height)
    dialog.add(pollingTimeField, gbc)

    gbc.gridx = 4

    gbc.weightx = 0.0
    val disableAfterNotifyCheckbox = JCheckBox("Delete after notify", true)
    dialog.add(disableAfterNotifyCheckbox, gbc)

    gbc.gridx = 5

    gbc.weightx = 0.0
    gbc.anchor = GridBagConstraints.CENTER
    val saveButton = JButton("Save")
    saveButton.addActionListener {
        SwingUtilities.invokeLater {
            onSaveFeed(
                feedUrlField.text,
                pollingTimeField.value as Int,
                disableAfterNotifyCheckbox.isSelected
            )
        }
        dialog.dispose()
    }
    dialog.add(saveButton, gbc)

    dialog.pack()
    dialog.setLocationRelativeTo(null)
    dialog.isVisible = true
}