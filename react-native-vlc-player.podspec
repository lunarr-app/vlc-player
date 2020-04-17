package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name            = 'react-native-vlc-player'
  s.version         = package['version']
  s.summary         = 'VLC Player for React Native'
  s.description     = package['description']
  s.author          = package['author']
  s.homepage        = package['repository']['url']
  s.license         = package['license']

  s.platform        = :ios, '9.0'
  s.source          = { git: 'https://github.com/lunarr-app/vlc-player.git', tag: 'master' }
  s.preserve_paths  = 'ios/**/*'
  s.source_files    = 'ios/**/*.{h,m}'
  s.requires_arc    = true
  # s.libraries           = 'bz2', 'iconv'
  # s.framework           = 'AudioToolbox','AVFoundation', 'CFNetwork', 'CoreFoundation', 'CoreGraphics', 'CoreMedia', 'CoreText', 'CoreVideo', 'Foundation', 'OpenGLES', 'QuartzCore', 'Security', 'VideoToolbox', 'UIKit'

  s.dependency      'React'
  s.dependency      'MobileVLCKit','~> 3.3.9'
end
