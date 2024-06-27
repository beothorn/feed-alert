import java.awt.*
import java.awt.event.ActionEvent
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

        // Create an image icon
        val iconImage = ImageIcon("path_to_your_icon.png").image

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