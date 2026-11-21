# Keep org.json reflection-free usage; nothing special needed.

# Glance records which GlanceAppWidget class each widget receiver uses, by class name.
# Keep those classes separate and un-renamed so R8 can't merge MoonWidget into SkyWidget
# (which drew one layout into both widgets) or rename them between releases.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
