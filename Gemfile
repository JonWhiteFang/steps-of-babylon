# Pinned Jekyll + minima for the GitLab Pages job (site/ privacy policy). site/_config.yml declares
# `theme: minima`, so a bare `jekyll` install won't resolve it — both are pinned + locked (Gemfile.lock).
source "https://rubygems.org"
gem "jekyll", "4.3.3"
gem "minima", "2.5.1"

# Security floor on a TRANSITIVE gem (sass-embedded -> google-protobuf), not a direct build dependency.
# google-protobuf < 3.25.5 carries a DoS advisory (GHSA/Dependabot alert 34); the locked 3.23.4 was
# vulnerable. Declaring the floor here is what keeps the resolution pinned — `bundle lock
# --update=google-protobuf` alone cascades to protobuf 4.x by also upgrading sass-embedded, which
# needlessly churns a Pages toolchain already proven green on the Phase-1 scratch import.
# `< 4` holds us inside the 3.x line that sass-embedded 1.58.3 requires (`~> 3.21`).
# CI-only: this Gemfile builds the privacy-policy site. Nothing here ships in the app.
gem "google-protobuf", ">= 3.25.5", "< 4"
