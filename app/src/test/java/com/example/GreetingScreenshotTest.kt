package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AnimatedWashingBubbles
import com.example.ui.ApnaDhobiViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { 
      MyApplicationTheme { 
        AnimatedWashingBubbles() 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun test_splash_screen_render() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ApnaDhobiViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        SplashScreen(vm)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_login_screen_render() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ApnaDhobiViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreen(vm)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_location_selection_screen_render() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ApnaDhobiViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        LocationSelectionScreen(vm)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_main_viewport_screen_render() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ApnaDhobiViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        MainViewport(vm)
      }
    }
    composeTestRule.waitForIdle()
  }
}

