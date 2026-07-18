# 平台服务器终端默认配色。只影响当前 PTY，不修改用户主目录或系统配置。
export TERM="${TERM:-xterm-256color}"
export COLORTERM="${COLORTERM:-truecolor}"
export CLICOLOR="${CLICOLOR:-1}"

# 用户、服务器和当前目录使用不同颜色；\$ 会按实际 UID 显示 $ 或 #。
PS1='\[\e[32m\]\u@\h\[\e[0m\]:\[\e[34m\]\w\[\e[0m\]\$ '

case "$(uname -s)" in
  Darwin)
    alias ls='ls -G'
    ;;
  Linux)
    alias ls='ls --color=auto'
    ;;
esac

alias grep='grep --color=auto'
alias egrep='egrep --color=auto'
alias fgrep='fgrep --color=auto'

# Git 在交互式终端按 ANSI 能力输出颜色，不写入用户或系统 Git 配置。
alias git='git -c color.ui=auto'

# 保持现有行为：用户自己的交互配置最后加载，并可覆盖上述平台默认值。
if [ -r "$HOME/.bashrc" ]; then
  . "$HOME/.bashrc"
fi
