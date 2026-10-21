using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace CookieLauncher.UI;

/// <summary>Maps <c>true</c> to <see cref="Visibility.Visible"/> and <c>false</c> to collapsed.</summary>
/// <remarks>
/// Declared as a plain converter rather than a markup extension so App.xaml can
/// instantiate it as a resource, which is what the styles expect.
/// </remarks>
public sealed class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is true ? Visibility.Visible : Visibility.Collapsed;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is Visibility.Visible;
}

/// <summary>Maps <c>true</c> to collapsed and <c>false</c> to visible.</summary>
public sealed class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is true ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is not Visibility.Visible;
}
