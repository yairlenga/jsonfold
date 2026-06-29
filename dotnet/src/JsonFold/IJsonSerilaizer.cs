public interface IJsonSerializer
{
    string Serialize<T>(T value);
}