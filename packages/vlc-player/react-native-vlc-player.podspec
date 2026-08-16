require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name            = 'react-native-vlc-player'
  s.version         = package['version']
  s.summary         = package['description']
  s.description     = package['description']
  s.author          = package['author']
  s.homepage        = package['repository']['url']
  s.source          = { git: package['repository']['url'], tag: "v#{package['version']}" }
  s.license         = package['license']

  s.module_name     = 'RCTVLCPlayerModule'
  s.platforms       = { :ios => '15.1', :tvos => '15.1' }
  s.swift_version   = '5.0'
  s.static_framework = true

  s.source_files = 'ios/**/*.{h,m,mm,swift}'

  # React-Core-prebuilt headers are Objective-C++ and cannot be parsed inside
  # the Swift module umbrella (`atomic`/`memory`/libc++ not found). Keep the
  # consumer-facing ObjC++ headers private so the generated Swift module stays
  # free of React C++ headers. Our .mm files import them via the build dir.
  s.public_header_files = []
  s.private_header_files = 'ios/**/*.h'

  # Module settings must be set BEFORE install_modules_dependencies: that helper
  # reads pod_target_xcconfig and merges the New Architecture HEADER_SEARCH_PATHS
  # (notably "$(PODS_ROOT)/Headers/Private/Yoga") and OTHER_CPLUSPLUSFLAGS (the
  # React-VFS overlay). Assigning after it, or declaring HEADER_SEARCH_PATHS
  # here, would overwrite those and break Fabric header includes.
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule',
    # Enable Swift/C++ interop surface needed by the thin Fabric wrapper.
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++20',
    # Required so the generated Swift module umbrella can include the
    # React-Core-prebuilt Objective-C++ headers (libc++ stdlib headers).
    'CLANG_CXX_LIBRARY' => 'libc++',
    'OTHER_LDFLAGS' => '$(inherited) -lc++',
  }

  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency 'React-Core'
    s.dependency 'React-RCTFabric'
    s.dependency 'ReactCommon'
  end

  # libvlc ships separate binaries for iOS and tvOS (MobileVLCKit vs TVVLCKit),
  # versioned in lockstep (both 3.7.3). A single podspec with a platform-conditional
  # dependency is required because autolinkers (Expo's included) pick exactly one
  # podspec per package with no platform awareness - the "-tvos" naming convention
  # is not honored, so a second podspec would be selected for every platform.
  #
  # The target platform is detected from the Podfile file itself. Evaluating it
  # would be unsafe: podspecs can be evaluated while the Podfile is still being
  # loaded (Expo's autolinking manager does this eagerly), so accessing
  # Pod::Config.instance.podfile would trigger a re-entrant Podfile evaluation.
  podfile_path = Pod::Config.instance.podfile_path || (Pathname.new(Dir.pwd) + 'Podfile')
  podfile_content = File.exist?(podfile_path) ? File.read(podfile_path) : ''
  tvos = podfile_content.match?(/^\s*platform\s*:\s*tvos\b/)
  s.dependency tvos ? 'TVVLCKit' : 'MobileVLCKit', '~> 3.7.3'
end
