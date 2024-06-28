import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.lang.System.currentTimeMillis
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.*


data class RssEntry(val url: String, val pollingIntervalInSeconds: Int, val disableAfterNotification: Boolean)
data class RssEntryWithSchedule(val triggerAtTimestamp: Long, val rssEntry: RssEntry, val title: String)

val rssEntries = arrayListOf<RssEntry>()

val comparator = compareBy<RssEntryWithSchedule> { it.triggerAtTimestamp }
val schedule = PriorityQueue(comparator)
val scheduler = Executors.newScheduledThreadPool(1)
var scheduledFuture: ScheduledFuture<*> = scheduler.schedule({}, 5, SECONDS)
var trayIcon: TrayIcon? = null

fun main() {
    if (!SystemTray.isSupported()) throw RuntimeException("System tray is not supported on this platform.")

    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val actions = mapOf(
        "Add Feed" to onAddFeed,
        "Exit" to onExit
    )

    trayIcon = createTrayIcon(actions)
}

fun createTrayIcon(actions: Map<String, ActionListener>): TrayIcon {
    val tray = SystemTray.getSystemTray()
    val iconUrl:URL? = {}::class.java.getResource("/icon.png")
    if (iconUrl == null)  throw RuntimeException("Icon resource not found")
    val iconImage = ImageIcon(iconUrl).image

    val popup = PopupMenu()

    for ((name, action) in actions) {
        val menuItem = MenuItem(name)
        menuItem.addActionListener(action)
        popup.add(menuItem)
    }

    val trayIcon = TrayIcon(iconImage, "rss-alert", popup)
    trayIcon.isImageAutoSize = true
    tray.add(trayIcon)
    return trayIcon
}

val task = Runnable {
    println("Running schedule!!!")
    val nextToRead = schedule.poll()
    val currentTime = currentTimeMillis()
    println("Now is '$currentTime', should run at '${nextToRead.triggerAtTimestamp}'")
    println("This is '${nextToRead.triggerAtTimestamp - currentTime}' millis")
    if (currentTime >= nextToRead.triggerAtTimestamp) {
        println("Will read entry.")
        val rssEntry = nextToRead.rssEntry
        val firstRssEntry = readFirstEntry(rssEntry.url)
        val firstRssEntryTitle = firstRssEntry.title.orElse("")
        if (!firstRssEntryTitle.equals(nextToRead.title)) {
            println("Title changed!!!")
            println("was '$nextToRead.title' and now is '$firstRssEntryTitle'")
            trayIcon!!.displayMessage(
                firstRssEntry.title.orElse("No title"),
                firstRssEntry.description.orElse("No description"),
                TrayIcon.MessageType.INFO
            )

            if (!rssEntry.disableAfterNotification) {
                println("Title changed, will Reschedule")
                addNewSchedule(rssEntry.pollingIntervalInSeconds, rssEntry, firstRssEntryTitle)
            } else {
                println("Title changed, will NOT Reschedule")
            }
        } else {
            println("Title not changed, will reschedule")
            addNewSchedule(rssEntry.pollingIntervalInSeconds, rssEntry, firstRssEntryTitle)
        }
    }
    rescheduleTask()
}

fun rescheduleTask() {
    val futureRead = schedule.peek()
    scheduledFuture.cancel(false)
    val nextReadInMillis = futureRead.triggerAtTimestamp - currentTimeMillis()
    scheduledFuture = scheduler.schedule(
        task,
        nextReadInMillis,
        MILLISECONDS
    )
    println("Will read again in ${nextReadInMillis/1000} seconds")
}


val onExit = ActionListener { e: ActionEvent ->
    System.exit(0)
}

val onAddFeed = ActionListener { _: ActionEvent ->
    // Create the dialog for adding a feed
    val dialog = JDialog(null as Frame?, "Add Feed", true)
    dialog.layout = GridBagLayout()
    val gbc = GridBagConstraints()
    gbc.insets = Insets(10, 10, 10, 10)
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.gridy = 0

    gbc.gridx = 0
    dialog.add(JLabel("Feed URL:"), gbc)

    gbc.gridx = 1
    val feedUrlField = JTextField(20)
    dialog.add(feedUrlField, gbc)

    gbc.gridx = 2
    dialog.add(JLabel("Polling time (seconds):"), gbc)

    gbc.gridx = 3
    val feedPollingTimeModel: SpinnerModel = SpinnerNumberModel(
        300,
        0,
        Int.MAX_VALUE,
        1
    )
    val pollingTimeField = JSpinner(feedPollingTimeModel)
    dialog.add(pollingTimeField, gbc)

    gbc.gridx = 4
    val disableAfterNotifyCheckbox = JCheckBox("Disable after notify:", true)
    dialog.add(disableAfterNotifyCheckbox, gbc)

    gbc.gridx = 5
    gbc.anchor = GridBagConstraints.CENTER
    val saveButton = JButton("Save")
    saveButton.addActionListener {
        SwingUtilities.invokeLater {
            val feedUrl = feedUrlField.text
            val pollingTime = pollingTimeField.value as Int
            val disableAfterNotify = disableAfterNotifyCheckbox.isSelected

            // Process the feed entry as needed
            println("Feed URL: $feedUrl")
            println("Polling time: $pollingTime seconds")
            println("Disable after notify: $disableAfterNotify")

            val firstRssEntry = readFirstEntry(feedUrl)

            val rssEntry = RssEntry(feedUrl, pollingTime, disableAfterNotify)
            rssEntries.add(rssEntry)
            addNewSchedule(pollingTime, rssEntry, firstRssEntry.title.orElse(""))

            rescheduleTask()
            println("rssEntries")
            println(rssEntries)
            println("schedule")
            println(schedule)
        }
        dialog.dispose()
    }
    dialog.add(saveButton, gbc)

    // Display the dialog
    dialog.pack()
    dialog.setLocationRelativeTo(null)  // Center the dialog
    dialog.isVisible = true
}

private fun addNewSchedule(pollingIntervalInSeconds: Int, rssEntry: RssEntry, firstRssEntryTitle: String) {
    schedule.add(
        RssEntryWithSchedule(
            currentTimeMillis() + (pollingIntervalInSeconds * 1000),
            rssEntry,
            firstRssEntryTitle
        )
    )
}

private fun readFirstEntry(feedUrl: String?): Item {
    val rssReader = RssReader()
    val items: List<Item> = rssReader.read(feedUrl).toList()
    val firstRssEntry = items[0]
    println(firstRssEntry.title.orElse("No title"))
    println(firstRssEntry.description.orElse("No description"))
    return firstRssEntry
}