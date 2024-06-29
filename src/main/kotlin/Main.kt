import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import net.harawata.appdirs.AppDirs
import net.harawata.appdirs.AppDirsFactory
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.System.currentTimeMillis
import java.net.URI
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.*
import kotlin.system.exitProcess

data class RssEntry(
    var url: String,
    var pollingIntervalInSeconds: Int,
    var deleteAfterNotification: Boolean
) : Serializable

data class RssEntryWithSchedule(
    val triggerAtTimestamp: Long,
    val rssEntry: RssEntry,
    val title: String
)

val rssEntries = arrayListOf<RssEntry>()

val schedule = PriorityQueue(compareBy<RssEntryWithSchedule> { it.triggerAtTimestamp })
val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
var scheduledFuture: ScheduledFuture<*> = scheduler.schedule({}, 5, SECONDS)
var trayIcon: TrayIcon? = null
val menuItemEditFeeds = MenuItem("Delete Feeds")

val version = "1.0"

var rssFile: File? = null

fun main() {
    if (!SystemTray.isSupported()) throw RuntimeException("System tray is not supported on this platform.")

    val appDirs: AppDirs = AppDirsFactory.getInstance()
    val userDataDir = appDirs.getUserDataDir("feed-alert", null, "Beothorn")
    println(userDataDir)
    val directory = File(userDataDir)
    if (!directory.exists()) {
        directory.mkdirs()
    }
    rssFile = File(directory, "rssEntries")

    val entriesFromFile = readEntries()
    entriesFromFile.forEach { loadEntry(it) }

    println("Loaded ${rssEntries.size} items")

    setupLookAndFeel()
    trayIcon = createTrayIcon()
}

fun saveEntries() {
    println("Save ${rssEntries.size} items")
    rssFile?.let {
        FileOutputStream(it).use { fileOutputStream ->
            ObjectOutputStream(fileOutputStream).use { objectOutputStream ->
                objectOutputStream.writeObject(rssEntries)
            }
        }
    }
}

fun readEntries(): List<RssEntry> {
    rssFile?.let {
        if (!rssFile!!.exists()) {
            return arrayListOf()
        }
        FileInputStream(it).use { fileInputStream ->
            ObjectInputStream(fileInputStream).use { objectInputStream ->
                @Suppress("unchecked_cast")
                return objectInputStream.readObject() as List<RssEntry>
            }
        }
    }
    return arrayListOf()
}

val onAbout = ActionListener { _: ActionEvent ->
    val dialog = JDialog(
        null as Frame?,
        "About",
        true
    )
    dialog.setIconImage(getAppIcon())
    dialog.layout = BoxLayout(dialog.contentPane, BoxLayout.Y_AXIS)
    dialog.add(JLabel("Version $version"))
    val close = JButton("Close")
    close.addActionListener { dialog.dispose() }
    dialog.add(close)

    dialog.pack()
    dialog.setLocationRelativeTo(null)
    dialog.isVisible = true
}
val onAddFeed = ActionListener { _: ActionEvent -> showAddFeed(getAppIcon(), addNewFeed) }
val onRemoveFeed = ActionListener { _: ActionEvent -> showDeleteFeed(getAppIcon(), rssEntries, removeFeedAndSave) }

private fun setupLookAndFeel() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val defaultFont = Font("Arial", Font.PLAIN, 14)
        UIManager.put("Label.font", defaultFont)
        UIManager.put("Button.font", defaultFont)
        UIManager.put("CheckBox.font", defaultFont)
        UIManager.put("TextField.font", defaultFont)
        UIManager.put("Spinner.font", defaultFont)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun createTrayIcon(): TrayIcon {
    val tray = SystemTray.getSystemTray()

    val popup = PopupMenu()

    menuItemEditFeeds.addActionListener(onRemoveFeed)
    popup.add(menuItemEditFeeds)
    menuItemEditFeeds.isEnabled = rssEntries.size > 0

    val actions = mapOf(
        "Add Feed" to onAddFeed,
        "About" to onAbout,
        "Exit" to onExit
    )

    for ((name, action) in actions) {
        val menuItem = MenuItem(name)
        menuItem.addActionListener(action)
        popup.add(menuItem)
    }

    val trayIcon = TrayIcon(getAppIcon(), "rss-alert", popup)
    trayIcon.isImageAutoSize = true
    tray.add(trayIcon)
    return trayIcon
}

private fun getAppIcon(): Image {
    val iconUrl: URL? = {}::class.java.getResource("/icon.png")
    if (iconUrl == null) {
        throw RuntimeException()
    }
    val iconImage: Image = ImageIcon(iconUrl).image
    return iconImage
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
        val maybeFirstRssEntry = readFirstEntry(rssEntry.url)
        if (maybeFirstRssEntry.isPresent) {
            val firstRssEntry = maybeFirstRssEntry.get()
            val firstRssEntryTitle = firstRssEntry.title.orElse("")
            println("Comparing titles: '${nextToRead.title}' and '$firstRssEntryTitle'")
            val titleChanged = !firstRssEntryTitle.equals(nextToRead.title)
            if (titleChanged) {
                println("Title changed!!!")
                println("was '${nextToRead.title}' and now is '$firstRssEntryTitle'")
                showNotification(firstRssEntry)
                if (!rssEntry.deleteAfterNotification) {
                    println("Title changed, will Reschedule")
                    addNewSchedule(rssEntry, firstRssEntryTitle)
                } else {
                    println("Title changed, will NOT Reschedule")
                }
            } else {
                println("Title not changed, will reschedule")
                println("Title not changed, will reschedule")
                addNewSchedule(rssEntry, nextToRead.title)
            }
        } else {
            println("Something went wrong getting feed, will reschedule")
            addNewSchedule(rssEntry, nextToRead.title)
        }
    }
    rescheduleTask()
}

private fun showNotification(firstRssEntry: Item) {
    val oldActionListeners = trayIcon!!.actionListeners
    oldActionListeners.forEach { trayIcon!!.removeActionListener(it) }
    trayIcon!!.addActionListener { _: ActionEvent ->
        firstRssEntry.link.ifPresent {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(it));
            }
        }
    }
    trayIcon!!.displayMessage(
        firstRssEntry.title.orElse("No title"),
        firstRssEntry.description.orElse("No description"),
        TrayIcon.MessageType.INFO
    )
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
    println("Will read again in ${nextReadInMillis / 1000} seconds")
}


val onExit = ActionListener { _: ActionEvent ->
    println("Bye bye!")
    scheduledFuture.cancel(true)
    exitProcess(0)
}

val addNewFeed: (feedUrl: String, pollingTime: Int, disableAfterNotify: Boolean) -> Unit =
    { feedUrl, pollingTime, disableAfterNotify ->
        println("Feed URL: $feedUrl")
        println("Polling time: $pollingTime seconds")
        println("Disable after notify: $disableAfterNotify")

        val rssEntry = RssEntry(feedUrl, pollingTime, disableAfterNotify)

        loadEntry(rssEntry)
        saveEntries()

        rescheduleTask()

        println("rssEntries")
        println(rssEntries)
        println("schedule")
        println(schedule)

        menuItemEditFeeds.isEnabled = rssEntries.size > 0
    }

private fun loadEntry(rssEntry: RssEntry) {
    val firstRssEntryResult = readFirstEntryTitle(rssEntry.url)
    removeFeed(rssEntry.url)
    rssEntries.add(rssEntry)
    addNewSchedule(rssEntry, firstRssEntryResult)
}

val removeFeed: (String) -> Unit = { feedUrl: String ->
    println("Removed '$feedUrl'")
    rssEntries.removeIf { it.url == feedUrl }
    schedule.removeIf { it.rssEntry.url == feedUrl }
    menuItemEditFeeds.isEnabled = rssEntries.size > 0
}

val removeFeedAndSave: (String) -> Unit = { feedUrl: String ->
    removeFeed(feedUrl)
    saveEntries()
}

private fun addNewSchedule(
    rssEntry: RssEntry,
    firstRssEntryTitle: String
) {
    schedule.add(
        RssEntryWithSchedule(
            currentTimeMillis() + (rssEntry.pollingIntervalInSeconds * 1000),
            rssEntry,
            firstRssEntryTitle
        )
    )
    rescheduleTask()
}

private fun readFirstEntryTitle(feedUrl: String): String {
    val maybeFirstRssEntry = readFirstEntry(feedUrl)
    if (maybeFirstRssEntry.isEmpty) {
        return ""
    }
    val firstRssEntry = maybeFirstRssEntry.get()
    if (firstRssEntry.title.isEmpty) {
        return ""
    }
    println(firstRssEntry.title.orElse("No title on rss"))
    println(firstRssEntry.description.orElse("No description"))
    return firstRssEntry.title.get()
}

private fun readFirstEntry(feedUrl: String): Optional<Item> {
    try {
        val rssReader = RssReader()
        val items: List<Item> = rssReader.read(feedUrl).toList()
        if (items.isEmpty()) {
            return Optional.empty()
        }
        val firstRssEntry = items[0]
        return Optional.of(firstRssEntry)
    } catch (e: Exception) {
        println(e.message)
        return Optional.empty()
    }
}