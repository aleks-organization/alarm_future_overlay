Pod::Spec.new do |s|
  s.name             = 'alarm_future_overlay'
  s.version          = '0.1.0'
  s.summary          = 'A Flutter plugin for full-screen alarm overlays on iOS.'
  s.homepage         = 'https://github.com/example/alarm_future_overlay'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '13.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.swift_version = '5.0'
end
