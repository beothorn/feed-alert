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
import kotlin.system.exitProcess


data class RssEntry(val url: String, val pollingIntervalInSeconds: Int, val deleteAfterNotification: Boolean)
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
        val defaultFont = Font("Arial", Font.PLAIN, 14)
        UIManager.put("Label.font", defaultFont)
        UIManager.put("Button.font", defaultFont)
        UIManager.put("CheckBox.font", defaultFont)
        UIManager.put("TextField.font", defaultFont)
        UIManager.put("Spinner.font", defaultFont)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val actions = mapOf(
        "Edit Feed" to onEditFeed,
        "Add Feed" to onAddFeed,
        "Exit" to onExit
    )

    trayIcon = createTrayIcon(actions)
}

fun createTrayIcon(actions: Map<String, ActionListener>): TrayIcon {
    val tray = SystemTray.getSystemTray()

    val popup = PopupMenu()

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
        val firstRssEntry = readFirstEntry(rssEntry.url)
        val firstRssEntryTitle = firstRssEntry.title.orElse("")
        println("Comparing titles: '${nextToRead.title}' and '$firstRssEntryTitle'")
        if (!firstRssEntryTitle.equals(nextToRead.title)) {
            println("Title changed!!!")
            println("was '${nextToRead.title}' and now is '$firstRssEntryTitle'")
            trayIcon!!.displayMessage(
                firstRssEntry.title.orElse("No title"),
                firstRssEntry.description.orElse("No description"),
                TrayIcon.MessageType.INFO
            )
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
    exitProcess(0)
}

val addNewFeed:(feedUrl: String, pollingTime: Int, disableAfterNotify: Boolean) -> Unit = { feedUrl, pollingTime, disableAfterNotify ->
    println("Feed URL: $feedUrl")
    println("Polling time: $pollingTime seconds")
    println("Disable after notify: $disableAfterNotify")

    val firstRssEntry = readFirstEntry(feedUrl)

    removeFeed(feedUrl)

    val rssEntry = RssEntry(feedUrl, pollingTime, disableAfterNotify)
    rssEntries.add(rssEntry)
    addNewSchedule(pollingTime, rssEntry, firstRssEntry.title.orElse(""))

    rescheduleTask()

    println("rssEntries")
    println(rssEntries)
    println("schedule")
    println(schedule)
}

val removeFeed:(String)-> Unit = { feedUrl: String ->
    println("Removed '$feedUrl'")
    rssEntries.removeIf { it.url == feedUrl }
    schedule.removeIf {it.rssEntry.url == feedUrl }
}

val onAddFeed = ActionListener { _: ActionEvent -> showAddFeed(getAppIcon(), addNewFeed) }

val onEditFeed = ActionListener { _: ActionEvent -> showEditFeed(getAppIcon(), rssEntries, removeFeed) }

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