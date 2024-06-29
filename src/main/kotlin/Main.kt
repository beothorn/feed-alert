import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
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

data class RssEntry(val url: String, val pollingIntervalInSeconds: Int, val deleteAfterNotification: Boolean)
data class RssEntryWithSchedule(val triggerAtTimestamp: Long, val rssEntry: RssEntry, val title: String)

val rssEntries = arrayListOf<RssEntry>()

val schedule = PriorityQueue(compareBy<RssEntryWithSchedule> { it.triggerAtTimestamp })
val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
var scheduledFuture: ScheduledFuture<*> = scheduler.schedule({}, 5, SECONDS)
var trayIcon: TrayIcon? = null
val menuItemEditFeeds = MenuItem("Delete Feeds")

fun main() {
    if (!SystemTray.isSupported()) throw RuntimeException("System tray is not supported on this platform.")
    setupLookAndFeel()
    trayIcon = createTrayIcon()
}

val onAddFeed = ActionListener { _: ActionEvent -> showAddFeed(getAppIcon(), addNewFeed) }
val onEditFeed = ActionListener { _: ActionEvent -> showEditFeed(getAppIcon(), rssEntries, removeFeed) }

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

    menuItemEditFeeds.addActionListener(onEditFeed)
    popup.add(menuItemEditFeeds)
    menuItemEditFeeds.isEnabled = rssEntries.size > 0

    val actions = mapOf(
        "Add Feed" to onAddFeed,
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
                    addNewSchedule(rssEntry.pollingIntervalInSeconds, rssEntry, firstRssEntryTitle)
                } else {
                    println("Title changed, will NOT Reschedule")
                }
            } else {
                println("Title not changed, will reschedule")
                println("Title not changed, will reschedule")
                addNewSchedule(rssEntry.pollingIntervalInSeconds, rssEntry, nextToRead.title)
            }
        } else {
            println("Something went wrong getting feed, will reschedule")
            addNewSchedule(rssEntry.pollingIntervalInSeconds, rssEntry, nextToRead.title)
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
    println("Will read again in ${nextReadInMillis/1000} seconds")
}


val onExit = ActionListener { _: ActionEvent ->
    println("Bye bye!")
    scheduledFuture.cancel(true)
    exitProcess(0)
}

val addNewFeed:(feedUrl: String, pollingTime: Int, disableAfterNotify: Boolean) -> Unit = { feedUrl, pollingTime, disableAfterNotify ->
    println("Feed URL: $feedUrl")
    println("Polling time: $pollingTime seconds")
    println("Disable after notify: $disableAfterNotify")

    val firstRssEntryResult = readFirstEntryTitle(feedUrl)

    removeFeed(feedUrl)

    val rssEntry = RssEntry(feedUrl, pollingTime, disableAfterNotify)
    rssEntries.add(rssEntry)
    addNewSchedule(pollingTime, rssEntry, firstRssEntryResult)

    rescheduleTask()

    println("rssEntries")
    println(rssEntries)
    println("schedule")
    println(schedule)

    menuItemEditFeeds.isEnabled = rssEntries.size > 0
}

val removeFeed:(String)-> Unit = { feedUrl: String ->
    println("Removed '$feedUrl'")
    rssEntries.removeIf { it.url == feedUrl }
    schedule.removeIf {it.rssEntry.url == feedUrl }
    menuItemEditFeeds.isEnabled = rssEntries.size > 0
}

private fun addNewSchedule(
    pollingIntervalInSeconds: Int,
    rssEntry: RssEntry,
    firstRssEntryTitle: String
) {
    schedule.add(
        RssEntryWithSchedule(
            currentTimeMillis() + (pollingIntervalInSeconds * 1000),
            rssEntry,
            firstRssEntryTitle
        )
    )
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