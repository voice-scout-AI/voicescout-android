package com.voicescout.voicescout_android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.voicescout.voicescout_android.common.BannerAdapter
import com.voicescout.voicescout_android.common.BannerCard
import com.voicescout.voicescout_android.record.RecordActivity
import com.voicescout.voicescout_android.storage.StorageActivity

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageView
    private lateinit var bannerViewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var bannerAdapter: BannerAdapter
    
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private val AUTO_SLIDE_DELAY = 3000L // 3초마다 자동 슬라이드

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        menuButton = findViewById(R.id.toolbar_menu)
        bannerViewPager = findViewById(R.id.bannerViewPager)
        indicatorLayout = findViewById(R.id.bannerIndicatorLayout)

        setupBanner()

        findViewById<LinearLayout>(R.id.btn_record_view).setOnClickListener {
            val intent = Intent(this, RecordActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.btn_storage_view).setOnClickListener {
            val intent = Intent(this, StorageActivity::class.java)
            startActivity(intent)
        }

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupBanner() {
        // 배너 카드 데이터 생성 (card1.png ~ card6.png)
        val bannerCards = listOf(
            BannerCard(R.drawable.card1),
            BannerCard(R.drawable.card2),
            BannerCard(R.drawable.card3),
            BannerCard(R.drawable.card4),
            BannerCard(R.drawable.card5),
            BannerCard(R.drawable.card6)
        )

        bannerAdapter = BannerAdapter(bannerCards)
        bannerViewPager.adapter = bannerAdapter

        // 중간 위치에서 시작하여 무한 스크롤 효과
        val startPosition = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % bannerCards.size)
        bannerViewPager.setCurrentItem(startPosition, false)

        // 인디케이터 설정
        setupIndicators(bannerCards.size)

        // 페이지 변경 콜백 설정
        bannerViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position % bannerCards.size)
                resetAutoSlide()
            }
        })

        // 자동 슬라이드 시작
        startAutoSlide()
    }

    private fun setupIndicators(count: Int) {
        indicatorLayout.removeAllViews()
        
        for (i in 0 until count) {
            val indicator = ImageView(this)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 0, 8, 0)
            indicator.layoutParams = layoutParams
            indicator.setImageResource(R.drawable.indicator_inactive)
            indicatorLayout.addView(indicator)
        }
        
        // 첫 번째 인디케이터 활성화
        if (count > 0) {
            (indicatorLayout.getChildAt(0) as ImageView).setImageResource(R.drawable.indicator_active)
        }
    }

    private fun updateIndicators(currentPosition: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val indicator = indicatorLayout.getChildAt(i) as ImageView
            if (i == currentPosition) {
                indicator.setImageResource(R.drawable.indicator_active)
            } else {
                indicator.setImageResource(R.drawable.indicator_inactive)
            }
        }
    }

    private fun startAutoSlide() {
        bannerRunnable = object : Runnable {
            override fun run() {
                val nextPosition = bannerViewPager.currentItem + 1
                bannerViewPager.setCurrentItem(nextPosition, true)
                bannerHandler.postDelayed(this, AUTO_SLIDE_DELAY)
            }
        }
        bannerHandler.postDelayed(bannerRunnable!!, AUTO_SLIDE_DELAY)
    }

    private fun resetAutoSlide() {
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        startAutoSlide()
    }

    override fun onResume() {
        super.onResume()
        startAutoSlide()
    }

    override fun onPause() {
        super.onPause()
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
    }
}