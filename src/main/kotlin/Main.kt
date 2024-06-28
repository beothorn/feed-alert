import java.awt.*
import java.awt.event.ActionEvent
import java.net.URL
import javax.swing.ImageIcon

fun main() {
    if (SystemTray.isSupported()) {
        createTrayIcon()
    } else {
        println("System tray is not supported on this platform.")
    }
}

fun createTrayIcon() {
    try {
        val tray = SystemTray.getSystemTray()

        // Ensure the path is correctly resolved
        val iconUrl:URL? = {}::class.java.getResource("/icon.png")
        if (iconUrl == null) {
            throw RuntimeException("Icon resource not found")
        }

        val iconImage = ImageIcon(iconUrl).image

        // Create popup menu
        val popup = PopupMenu()

        // Create a menu item
        val exitItem = MenuItem("Exit")
        exitItem.addActionListener { _: ActionEvent? ->
            // Handle exit action
            System.exit(0)
        }
        popup.add(exitItem)

        // Create a tray icon
        val trayIcon = TrayIcon(iconImage, "Tray Icon Example", popup)
        trayIcon.isImageAutoSize = true

        // Add the tray icon to the system tray
        tray.add(trayIcon)
        trayIcon.displayMessage("Hello, World", "Tray Icon Example Started", TrayIcon.MessageType.INFO)
    } catch (e: AWTException) {
        e.printStackTrace()
    }
}